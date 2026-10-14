package com.hms.util;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for retrieving secrets from AWS Secrets Manager.
 *
 * Cloud Readiness Fix (cr-java-0113 - Lack of Externalized Secrets):
 * This utility replaces all hardcoded credentials, API keys, and sensitive
 * configuration values with calls to AWS Secrets Manager. Centralising secret
 * retrieval here enables:
 *   - Automatic credential rotation without code changes
 *   - Audit logging of every secret access via AWS CloudTrail
 *   - Fine-grained IAM access control over individual secrets
 *   - Elimination of credentials from source code and property files
 *
 * Cloud Readiness Fix (cr-java-0097 - Missing Connection Timeouts):
 * Adds explicit connection, socket, and API call timeouts to the AWS SDK
 * Secrets Manager client via {@link ClientConfiguration} to prevent indefinite
 * hangs and resource exhaustion in cloud environments:
 *   - connectionTimeout: max time (ms) to establish a TCP connection to the
 *     Secrets Manager endpoint
 *   - socketTimeout: max time (ms) to wait for data on an established socket
 *     (covers slow or unresponsive service responses)
 *   - requestTimeout: max total time (ms) for a single API request, including
 *     retries, before the SDK throws an exception
 *   - clientExecutionTimeout: max total time (ms) for the entire client-side
 *     execution of an API call (all retries combined)
 *   - maxErrorRetry: bounded retry count to avoid unbounded retry storms
 *
 * Usage example:
 *   String dbPassword = AwsSecretsManagerUtil.getSecretValue("hms/db/password");
 *   String dbUser     = AwsSecretsManagerUtil.getSecretValue("hms/db/username");
 *
 * The AWS region is resolved from the environment variable AWS_REGION (or the
 * default region configured in the execution environment / IAM instance profile).
 */
public class AwsSecretsManagerUtil {

    /** Environment variable that overrides the default AWS region. */
    private static final String AWS_REGION_ENV = "AWS_REGION";

    /** Default region used when AWS_REGION is not set. */
    private static final String DEFAULT_REGION = "us-east-1";

    // -----------------------------------------------------------------------
    // Timeout constants for AWS SDK client (cr-java-0097: Missing Connection Timeouts)
    // -----------------------------------------------------------------------

    /**
     * Maximum time (ms) to wait for a TCP connection to be established with
     * the AWS Secrets Manager endpoint. Prevents indefinite blocking during
     * network outages or DNS resolution failures.
     */
    private static final int AWS_SDK_CONNECTION_TIMEOUT_MS = 5_000;   // 5 seconds

    /**
     * Maximum time (ms) to wait for data on an already-established socket.
     * Guards against slow or unresponsive Secrets Manager responses.
     */
    private static final int AWS_SDK_SOCKET_TIMEOUT_MS = 10_000;      // 10 seconds

    /**
     * Maximum total time (ms) for a single API request attempt (excluding
     * retries). The SDK will abort the attempt and retry if this is exceeded.
     */
    private static final int AWS_SDK_REQUEST_TIMEOUT_MS = 10_000;     // 10 seconds

    /**
     * Maximum total time (ms) for the entire client-side execution of an API
     * call, including all retry attempts. Provides an absolute upper bound on
     * how long a secret retrieval can block the calling thread.
     */
    private static final int AWS_SDK_CLIENT_EXECUTION_TIMEOUT_MS = 30_000; // 30 seconds

    /**
     * Maximum number of retry attempts for failed Secrets Manager API calls.
     * Bounded retries prevent unbounded retry storms in degraded environments.
     */
    private static final int AWS_SDK_MAX_ERROR_RETRY = 3;

    // -----------------------------------------------------------------------

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Private constructor – utility class, not meant to be instantiated.
    private AwsSecretsManagerUtil() {
    }

    /**
     * Builds an {@link AWSSecretsManager} client with explicit connection,
     * socket, and API call timeouts configured (cr-java-0097).
     *
     * @param region the AWS region to target
     * @return a configured {@link AWSSecretsManager} client
     */
    private static AWSSecretsManager buildClient(String region) {
        // cr-java-0097: Configure explicit timeouts on the AWS SDK client to
        // prevent indefinite hangs and resource exhaustion in cloud environments.
        ClientConfiguration clientConfig = new ClientConfiguration()
                .withConnectionTimeout(AWS_SDK_CONNECTION_TIMEOUT_MS)
                .withSocketTimeout(AWS_SDK_SOCKET_TIMEOUT_MS)
                .withRequestTimeout(AWS_SDK_REQUEST_TIMEOUT_MS)
                .withClientExecutionTimeout(AWS_SDK_CLIENT_EXECUTION_TIMEOUT_MS)
                .withMaxErrorRetry(AWS_SDK_MAX_ERROR_RETRY);

        return AWSSecretsManagerClientBuilder.standard()
                .withRegion(region)
                .withClientConfiguration(clientConfig)
                .build();
    }

    /**
     * Retrieves the secret string for the given secret name from AWS Secrets Manager.
     *
     * @param secretName the name or ARN of the secret (e.g. "hms/db/password")
     * @return the plaintext secret value
     * @throws RuntimeException if the secret cannot be retrieved
     */
    public static String getSecretValue(String secretName) {
        String region = System.getenv(AWS_REGION_ENV);
        if (region == null || region.isEmpty()) {
            region = DEFAULT_REGION;
        }

        AWSSecretsManager client = buildClient(region);

        GetSecretValueRequest request = new GetSecretValueRequest()
                .withSecretId(secretName);

        GetSecretValueResult result = client.getSecretValue(request);
        return result.getSecretString();
    }

    /**
     * Retrieves a specific key from a JSON-formatted secret stored in AWS Secrets Manager.
     *
     * <p>Many secrets are stored as JSON objects, e.g.:
     * <pre>{"username":"root","password":"s3cr3t"}</pre>
     * This helper parses the JSON and returns the value for the requested key.
     *
     * @param secretName the name or ARN of the secret
     * @param key        the JSON key whose value should be returned
     * @return the value associated with {@code key} inside the secret JSON
     * @throws RuntimeException if the secret cannot be retrieved or parsed
     */
    public static String getSecretField(String secretName, String key) {
        try {
            String secretJson = getSecretValue(secretName);
            JsonNode node = OBJECT_MAPPER.readTree(secretJson);
            JsonNode fieldNode = node.get(key);
            if (fieldNode == null) {
                throw new RuntimeException(
                        "Key '" + key + "' not found in secret '" + secretName + "'");
            }
            return fieldNode.asText();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to retrieve field '" + key + "' from secret '" + secretName + "'", e);
        }
    }
}
