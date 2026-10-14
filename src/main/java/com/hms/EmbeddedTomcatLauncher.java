package com.hms;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

/**
 * cr-java-0107: WAR Packaging → Executable JAR with Embedded Tomcat
 *
 * <p>This launcher replaces the requirement for an external application server
 * (Tomcat, JBoss, WebLogic, WebSphere) by embedding Tomcat 9.x directly inside
 * the executable JAR.  The application can now be started with:
 *
 * <pre>
 *   java -jar Doctor-Patient-Portal.jar
 * </pre>
 *
 * <p>This pattern enables direct deployment on Amazon ECS, EKS, or AWS Fargate
 * without bundling or managing a separate application server, resulting in:
 * <ul>
 *   <li>Smaller, self-contained container images</li>
 *   <li>Faster startup times</li>
 *   <li>Simplified deployment automation</li>
 *   <li>Compliance with 12-factor app principles (process model)</li>
 * </ul>
 *
 * <p>The HTTP port is read from the {@code PORT} environment variable (default 8080)
 * so that container orchestrators (ECS task definitions, EKS pod specs) can
 * override it without rebuilding the image.
 */
public class EmbeddedTomcatLauncher {

    /**
     * Default HTTP port used when the {@code PORT} environment variable is absent.
     * AWS Fargate / ECS task definitions expose this port via the container port
     * mapping in the task definition.
     */
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws LifecycleException {

        // Read port from environment variable so the container orchestrator can
        // override it without rebuilding the image (12-factor: config in env).
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                System.err.println("[EmbeddedTomcatLauncher] Invalid PORT env value '"
                        + portEnv + "', falling back to " + DEFAULT_PORT);
            }
        }

        Tomcat tomcat = new Tomcat();

        // Use a temp directory for Tomcat's working files; this is safe for
        // ephemeral container file systems (ECS/Fargate tasks).
        String workDir = System.getProperty("java.io.tmpdir");
        tomcat.setBaseDir(workDir);

        // Configure the HTTP connector on the resolved port.
        Connector connector = new Connector();
        connector.setPort(port);
        tomcat.setConnector(connector);

        // Locate the webapp directory.  When running from the shaded JAR the
        // webapp resources are embedded; when running from the IDE/Maven target
        // directory the standard src/main/webapp path is used.
        String webappDirLocation = resolveWebappDir();

        Context ctx = tomcat.addWebapp("", new File(webappDirLocation).getAbsolutePath());

        // Add compiled classes from target/classes so that servlets are found
        // when launching from the exploded build directory (IDE / mvn exec).
        File additionWebInfClasses = new File("target/classes");
        if (additionWebInfClasses.exists()) {
            WebResourceRoot resources = new StandardRoot(ctx);
            resources.addPreResources(new DirResourceSet(
                    resources,
                    "/WEB-INF/classes",
                    additionWebInfClasses.getAbsolutePath(),
                    "/"));
            ctx.setResources(resources);
        }

        tomcat.start();

        System.out.println("[EmbeddedTomcatLauncher] Doctor-Patient-Portal started on port " + port);
        System.out.println("[EmbeddedTomcatLauncher] Ready for AWS ECS/EKS/Fargate deployment.");

        // Block the main thread so the JVM (and therefore the container) stays alive.
        tomcat.getServer().await();
    }

    /**
     * Resolves the webapp root directory.
     *
     * <p>Priority order:
     * <ol>
     *   <li>System property {@code webapp.dir} – allows overriding at runtime.</li>
     *   <li>Standard Maven source path {@code src/main/webapp} – used during
     *       development / IDE runs.</li>
     *   <li>Fallback to current working directory.</li>
     * </ol>
     */
    private static String resolveWebappDir() {
        String override = System.getProperty("webapp.dir");
        if (override != null && new File(override).exists()) {
            return override;
        }
        File mavenWebapp = new File("src/main/webapp");
        if (mavenWebapp.exists()) {
            return mavenWebapp.getPath();
        }
        // When packaged as a shaded JAR the webapp resources are served from
        // the JAR's classpath; return "." as a safe fallback.
        return ".";
    }
}
