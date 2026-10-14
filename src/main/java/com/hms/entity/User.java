package com.hms.entity;

/**
 * User entity class representing an application user (patient or admin).
 *
 * <p><b>Cloud Readiness Fix — cr-java-0113 (Lack of Externalized Secrets)</b><br>
 * The original {@code toString()} implementation at line 75 of the source file
 * concatenated the {@code password} field directly into the returned string:
 * <pre>
 *   return "User [id=" + id + ", fullName=" + fullName
 *        + ", email=" + email + ", password=" + password + "]";
 * </pre>
 * This is a critical security vulnerability because:
 * <ul>
 *   <li>Any call to {@code toString()} (e.g. via logging, debugging, or exception
 *       messages) would expose the raw password in plain text.</li>
 *   <li>In cloud environments, application logs are typically forwarded to
 *       centralised observability services (e.g. AWS CloudWatch Logs). Passwords
 *       appearing in those logs violate the principle of least privilege and create
 *       an audit/compliance risk.</li>
 *   <li>Embedding or serialising credentials in code or diagnostic output prevents
 *       proper credential lifecycle management (rotation, revocation).</li>
 * </ul>
 *
 * <p><b>Remediation applied:</b>
 * <ol>
 *   <li>The {@code password} field has been <em>excluded</em> from
 *       {@code toString()} so it can never leak into logs or monitoring output.</li>
 *   <li>A utility class {@link com.hms.util.AwsSecretsManagerUtil} has been
 *       introduced to retrieve sensitive credentials (database passwords, API keys,
 *       encryption keys) from <b>AWS Secrets Manager</b> at runtime. This enables:
 *       <ul>
 *         <li>Centralised secret storage with fine-grained IAM access control.</li>
 *         <li>Automatic credential rotation without code changes.</li>
 *         <li>Full audit logging of every secret access via AWS CloudTrail.</li>
 *         <li>Complete elimination of credentials from source code and property files.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Secrets should be stored in AWS Secrets Manager under a path convention such as:
 * <pre>
 *   hms/db/credentials   →  {"username":"root","password":"&lt;managed-secret&gt;"}
 *   hms/app/admin        →  {"adminPassword":"&lt;managed-secret&gt;"}
 * </pre>
 * and retrieved via:
 * <pre>
 *   String dbPassword = AwsSecretsManagerUtil.getSecretField("hms/db/credentials", "password");
 * </pre>
 */
public class User {

    private int id;
    private String fullName;
    private String email;

    /**
     * The user's password.
     *
     * <p>This field is intentionally excluded from {@link #toString()} to prevent
     * credential leakage in logs and cloud monitoring systems (e.g. AWS CloudWatch).
     * In production, passwords must be managed exclusively through
     * <b>AWS Secrets Manager</b> and must never be embedded in source code,
     * property files, or serialised diagnostic output.
     */
    private String password;


    public User() {
        super();
    }


    public User(int id, String fullName, String email, String password) {
        super();
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }


    public User(String fullName, String email, String password) {
        super();
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }


    public int getId() {
        return id;
    }


    public void setId(int id) {
        this.id = id;
    }


    public String getFullName() {
        return fullName;
    }


    public void setFullName(String fullName) {
        this.fullName = fullName;
    }


    public String getEmail() {
        return email;
    }


    public void setEmail(String email) {
        this.email = email;
    }


    public String getPassword() {
        return password;
    }


    public void setPassword(String password) {
        this.password = password;
    }


    /**
     * Returns a safe string representation of this User.
     *
     * <p><b>Cloud Readiness Fix — cr-java-0113:</b><br>
     * The {@code password} field is intentionally <em>omitted</em> from this output.
     * The original source (line 75) included {@code ", password=" + password}, which
     * would expose raw credentials in any log line, exception message, or monitoring
     * dashboard that calls {@code toString()} on a {@code User} object.
     *
     * <p>In AWS cloud deployments, application logs are streamed to CloudWatch Logs
     * and may be indexed by third-party SIEM tools. Passwords must never appear in
     * these streams. All sensitive credentials must be stored in and retrieved from
     * <b>AWS Secrets Manager</b> via {@link com.hms.util.AwsSecretsManagerUtil}.
     *
     * @return a string containing only non-sensitive fields: id, fullName, and email
     */
    @Override
    public String toString() {
        // SECURITY: 'password' is deliberately excluded to prevent credential leakage
        // in application logs and cloud monitoring systems (cr-java-0113).
        return "User [id=" + id + ", fullName=" + fullName + ", email=" + email + "]";
    }
}
