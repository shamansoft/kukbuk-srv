package net.shamansoft.cookbook.security;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.KeyManagementServiceSettings;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for encrypting and decrypting OAuth tokens using Google Cloud KMS.
 *
 * Tokens are encrypted at rest in Firestore for security.
 */
@Service
@Slf4j
public class TokenEncryptionService {

    @Value("${gcp.kms.enabled:true}")
    private boolean kmsEnabled;

    @Value("${gcp.project-id:kukbuk-tf}")
    private String projectId;

    @Value("${gcp.kms.location:us-west1}")
    private String location;

    @Value("${gcp.kms.keyring:cookbook-keyring}")
    private String keyring;

    @Value("${gcp.kms.key:oauth-token-key}")
    private String keyName;

    private KeyManagementServiceClient kmsClient;

    /**
     * Initialize KMS client during bean creation with explicit quota project.
     * This ensures API calls are billed to the correct project.
     *
     * Can be disabled via gcp.kms.enabled=false (useful for tests).
     */
    @PostConstruct
    public void initKmsClient() {
        if (!kmsEnabled) {
            log.info("KMS client initialization skipped (gcp.kms.enabled=false)");
            return;
        }

        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

            // Set quota project to ensure KMS API calls use the correct project
            GoogleCredentials credentialsWithQuotaProject = credentials.createWithQuotaProject(projectId);

            log.debug("Initializing KMS client with quota project: {}, location: {}, keyring: {}, key: {}",
                     projectId, location, keyring, keyName);

            KeyManagementServiceSettings settings = KeyManagementServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentialsWithQuotaProject))
                .build();

            this.kmsClient = KeyManagementServiceClient.create(settings);

            log.info("KMS client initialized successfully for project: {}", projectId);

        } catch (IOException e) {
            log.error("Failed to initialize KMS client: {}", e.getMessage(), e);
            throw new RuntimeException("KMS client initialization failed", e);
        }
    }

    /**
     * Clean up KMS client on bean destruction
     */
    @PreDestroy
    public void closeKmsClient() {
        if (kmsClient != null) {
            log.info("Closing KMS client for project: {}", projectId);
            kmsClient.close();
        }
    }

    /**
     * Encrypt plaintext using Cloud KMS
     *
     * @param plaintext The plaintext to encrypt (e.g., OAuth token)
     * @return Base64-encoded ciphertext
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Plaintext cannot be null or blank");
        }

        try {
            CryptoKeyName cryptoKeyName = CryptoKeyName.of(
                projectId, location, keyring, keyName
            );

            ByteString plaintextBytes = ByteString.copyFrom(
                plaintext.getBytes(StandardCharsets.UTF_8)
            );

            var response = kmsClient.encrypt(cryptoKeyName, plaintextBytes);
            byte[] ciphertext = response.getCiphertext().toByteArray();

            String encrypted = Base64.getEncoder().encodeToString(ciphertext);
            log.debug("Successfully encrypted token");
            return encrypted;

        } catch (Exception e) {
            log.error("Failed to encrypt token: {}", e.getMessage());
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext using Cloud KMS
     *
     * @param encryptedToken Base64-encoded ciphertext
     * @return Decrypted plaintext
     */
    public String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw new IllegalArgumentException("Encrypted token cannot be null or blank");
        }

        try {
            CryptoKeyName cryptoKeyName = CryptoKeyName.of(
                projectId, location, keyring, keyName
            );

            byte[] ciphertext = Base64.getDecoder().decode(encryptedToken);
            ByteString ciphertextBytes = ByteString.copyFrom(ciphertext);

            var response = kmsClient.decrypt(cryptoKeyName, ciphertextBytes);
            String decrypted = response.getPlaintext().toString(StandardCharsets.UTF_8);

            log.debug("Successfully decrypted token");
            return decrypted;

        } catch (Exception e) {
            log.error("Failed to decrypt token: {}", e.getMessage());
            throw new RuntimeException("Token decryption failed", e);
        }
    }
}
