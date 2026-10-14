package com.hms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * RedisSessionConfig — cr-java-0065: HTTP Session State Storage
 *
 * Configures Spring Session to store all HTTP session data in Amazon ElastiCache
 * for Redis instead of in-process JVM memory.  This makes every application
 * instance completely stateless: any instance can serve any request regardless
 * of which instance originally created the session, enabling safe horizontal
 * scaling and zero session-data loss on instance termination or replacement.
 *
 * The @EnableRedisHttpSession annotation registers the SessionRepositoryFilter
 * bean that transparently wraps every javax.servlet.http.HttpSession with a
 * Redis-backed session via the DelegatingFilterProxy declared in web.xml.
 * No changes to existing servlet session API calls (req.getSession(),
 * session.setAttribute(), session.removeAttribute()) are required — the
 * filter intercepts them transparently and delegates to ElastiCache for Redis.
 *
 * Required environment variables (set in ECS task definition / Elastic
 * Beanstalk environment / Systems Manager Parameter Store):
 *   REDIS_HOST — ElastiCache primary endpoint
 *                (e.g. my-cluster.abc123.ng.0001.use1.cache.amazonaws.com)
 *   REDIS_PORT — ElastiCache port (default: 6379)
 *
 * Affected servlets (cr-java-0065 occurrences):
 *   - DoctorEditProfileServlet  (occurrences 1-4)
 *   - DoctorLoginServlet        (occurrences 5-8)
 *   - DoctorLogoutServlet       (occurrences 9-10)
 */
@Configuration
@EnableRedisHttpSession
public class RedisSessionConfig {

    /**
     * Redis host resolved from the REDIS_HOST environment variable.
     * Defaults to "localhost" for local development when the variable is absent.
     */
    @Value("${REDIS_HOST:localhost}")
    private String redisHost;

    /**
     * Redis port resolved from the REDIS_PORT environment variable.
     * Defaults to 6379 (standard Redis port) when the variable is absent.
     */
    @Value("${REDIS_PORT:6379}")
    private int redisPort;

    /**
     * Creates the Lettuce connection factory that Spring Session uses to
     * communicate with Amazon ElastiCache for Redis.
     *
     * Lettuce is the recommended non-blocking Redis client for Spring Session
     * and supports TLS connections required by ElastiCache in-transit
     * encryption when configured with a RedisSSLConfiguration.
     *
     * @return a configured {@link LettuceConnectionFactory}
     */
    @Bean
    public LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration redisConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(redisConfig);
    }
}
