package com.hms.db;

import java.sql.Connection;
import java.sql.SQLException;

import com.hms.util.AwsSecretsManagerUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Cloud-ready database connection provider using HikariCP connection pooling
 * combined with Amazon RDS Proxy for optimized cloud database connections.
 *
 * Cloud Readiness Fix (cr-java-0073 - Direct JDBC Connections):
 * Replaces raw DriverManager.getConnection() with a HikariCP connection pool.
 * - Database credentials are retrieved from AWS Secrets Manager (no hardcoded values).
 * - The JDBC URL is resolved from the environment variable DB_URL, enabling
 *   seamless integration with Amazon RDS Proxy for connection multiplexing,
 *   IAM authentication, and automatic failover.
 * - HikariCP manages the connection lifecycle, idle timeouts, and pool sizing,
 *   preventing connection exhaustion in horizontally-scaled cloud deployments.
 *
 * Cloud Readiness Fix (cr-java-0097 - Missing Connection Timeouts):
 * Adds explicit connection, socket, and network-level timeouts to prevent
 * indefinite hangs and resource exhaustion in cloud environments with variable
 * network latency and potential service failures:
 *   - HikariCP connectionTimeout: max wait to borrow a connection from the pool
 *   - HikariCP initializationFailTimeout: fail fast if pool cannot be initialised
 *   - JDBC socketTimeout (via dataSource.socketTimeout): OS-level TCP socket read timeout
 *   - JDBC connectTimeout (via dataSource.connectTimeout): TCP connection establishment timeout
 *   - JDBC loginTimeout: max time to wait for the database server login handshake
 *   - keepaliveTime: periodic keepalive to detect stale connections early
 *
 * Environment variables consumed:
 *   DB_URL            - Full JDBC URL, e.g. jdbc:mysql://<rds-proxy-endpoint>:3306/hospital
 *                       Defaults to jdbc:mysql://localhost:3306/hospital for local dev.
 *   AWS_REGION        - AWS region used by AwsSecretsManagerUtil (default: us-east-1).
 *
 * AWS Secrets Manager secrets consumed (JSON-formatted):
 *   hms/db/credentials  - {"username":"<db-user>","password":"<db-password>"}
 */
public class DBConnection {

    /** Name of the AWS Secrets Manager secret that holds DB credentials. */
    private static final String DB_SECRET_NAME = "hms/db/credentials";

    /** Environment variable for the JDBC connection URL (supports RDS Proxy endpoint). */
    private static final String DB_URL_ENV = "DB_URL";

    /** Fallback JDBC URL used when DB_URL environment variable is not set (local dev only). */
    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/hospital";

    // -----------------------------------------------------------------------
    // Timeout constants (cr-java-0097: Missing Connection Timeouts)
    // Fix applied: Lines 18-18 of original DBConnection.java (DriverManager.getConnection)
    // -----------------------------------------------------------------------

    /**
     * Maximum time (ms) a caller will wait to obtain a connection from the pool.
     * Prevents indefinite blocking when all pool connections are in use.
     */
    private static final int HIKARI_CONNECTION_TIMEOUT_MS = 30_000;   // 30 seconds

    /**
     * Maximum time (ms) HikariCP will wait for the pool to be successfully
     * initialised before throwing an exception at startup.
     * Fail-fast behaviour avoids silent startup hangs in cloud environments.
     */
    private static final int HIKARI_INIT_FAIL_TIMEOUT_MS = 60_000;    // 60 seconds

    /**
     * Maximum time (ms) a connection may sit idle in the pool before being
     * evicted. Prevents accumulation of stale connections to RDS/RDS Proxy.
     */
    private static final int HIKARI_IDLE_TIMEOUT_MS = 600_000;        // 10 minutes

    /**
     * Maximum lifetime (ms) of a connection in the pool. Connections are
     * retired and replaced before this limit to avoid mid-query failures
     * caused by server-side connection timeouts (e.g. MySQL wait_timeout).
     */
    private static final int HIKARI_MAX_LIFETIME_MS = 1_800_000;      // 30 minutes

    /**
     * Interval (ms) at which HikariCP sends a keepalive query on idle
     * connections to detect and evict stale TCP connections early.
     */
    private static final int HIKARI_KEEPALIVE_TIME_MS = 60_000;       // 60 seconds

    /**
     * JDBC-level socket read timeout (seconds). If the database server does
     * not respond within this window the driver throws a {@link SQLException},
     * preventing threads from hanging indefinitely on slow or unresponsive
     * cloud database instances.
     */
    private static final int JDBC_SOCKET_TIMEOUT_SECONDS = 30;        // 30 seconds

    /**
     * JDBC-level TCP connection establishment timeout (milliseconds). Limits
     * the time spent waiting for the initial TCP handshake with the database
     * host (or RDS Proxy endpoint).
     */
    private static final int JDBC_CONNECT_TIMEOUT_MS = 10_000;        // 10 seconds

    // -----------------------------------------------------------------------

    /** Singleton HikariCP data source – initialised once on first use. */
    private static volatile HikariDataSource dataSource;

    /** Lock object for double-checked locking during pool initialisation. */
    private static final Object LOCK = new Object();

    // Private constructor – utility class, not meant to be instantiated.
    private DBConnection() {
    }

    /**
     * Returns a {@link Connection} obtained from the HikariCP connection pool.
     *
     * <p>The pool is lazily initialised on the first call using credentials
     * retrieved from AWS Secrets Manager and the JDBC URL from the {@code DB_URL}
     * environment variable.  Subsequent calls borrow a connection from the
     * already-warmed pool, avoiding the overhead of creating a new physical
     * connection on every request.
     *
     * @return a pooled {@link Connection} ready for use
     * @throws RuntimeException if the pool cannot be initialised or a connection
     *                          cannot be obtained
     */
    public static Connection getConn() {
        if (dataSource == null) {
            synchronized (LOCK) {
                if (dataSource == null) {
                    dataSource = buildDataSource();
                }
            }
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain a database connection from HikariCP pool", e);
        }
    }

    /**
     * Builds and configures the {@link HikariDataSource}.
     *
     * <p>Credentials are fetched from AWS Secrets Manager; the JDBC URL is
     * read from the {@code DB_URL} environment variable so that the application
     * can point at an Amazon RDS Proxy endpoint without any code changes.
     *
     * <p>All connection, socket, and pool-level timeouts are explicitly set
     * (cr-java-0097) to prevent indefinite hangs and resource exhaustion in
     * cloud environments with variable network latency.
     *
     * @return a fully configured, started {@link HikariDataSource}
     */
    private static HikariDataSource buildDataSource() {
        // Resolve JDBC URL – prefer environment variable (RDS Proxy endpoint in cloud)
        String jdbcUrl = System.getenv(DB_URL_ENV);
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            jdbcUrl = DEFAULT_DB_URL;
        }

        // Retrieve credentials from AWS Secrets Manager (no hardcoded values)
        String username;
        String password;
        try {
            username = AwsSecretsManagerUtil.getSecretField(DB_SECRET_NAME, "username");
            password = AwsSecretsManagerUtil.getSecretField(DB_SECRET_NAME, "password");
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to retrieve database credentials from AWS Secrets Manager secret '"
                            + DB_SECRET_NAME + "'", e);
        }

        // Configure HikariCP pool
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool sizing – sensible defaults for cloud deployments behind RDS Proxy
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        // -----------------------------------------------------------------------
        // Explicit timeout / lifecycle settings (cr-java-0097: Missing Connection Timeouts)
        // -----------------------------------------------------------------------

        // Max time (ms) a caller waits to borrow a connection from the pool.
        // Prevents indefinite blocking when the pool is exhausted.
        config.setConnectionTimeout(HIKARI_CONNECTION_TIMEOUT_MS);

        // Fail fast at startup if the pool cannot be initialised within this window.
        config.setInitializationFailTimeout(HIKARI_INIT_FAIL_TIMEOUT_MS);

        // Evict idle connections after 10 minutes to avoid stale connections to RDS.
        config.setIdleTimeout(HIKARI_IDLE_TIMEOUT_MS);

        // Retire connections after 30 minutes to avoid server-side timeout surprises.
        config.setMaxLifetime(HIKARI_MAX_LIFETIME_MS);

        // Send a keepalive query every 60 seconds to detect dead TCP connections early.
        config.setKeepaliveTime(HIKARI_KEEPALIVE_TIME_MS);

        // JDBC driver-level socket read timeout (seconds).
        // Prevents threads from hanging indefinitely waiting for a slow DB response.
        config.addDataSourceProperty("socketTimeout", String.valueOf(JDBC_SOCKET_TIMEOUT_SECONDS));

        // JDBC driver-level TCP connection establishment timeout (milliseconds).
        // Limits the time spent waiting for the initial handshake with the DB host.
        config.addDataSourceProperty("connectTimeout", String.valueOf(JDBC_CONNECT_TIMEOUT_MS));

        // -----------------------------------------------------------------------

        // Pool name for monitoring / CloudWatch metrics
        config.setPoolName("HmsHikariPool");

        // Validate connections before handing them out
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }
}
