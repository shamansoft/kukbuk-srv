package net.shamansoft.cookbook.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@Slf4j
public class FirestoreConfig {

    @Value("${google.cloud.project-id:cookbook-extractor}")
    private String projectId;

    @Value("${google.cloud.credentials.path:}")
    private String credentialsPath;

    @Bean
    @ConditionalOnProperty(name = "firestore.enabled", havingValue = "true", matchIfMissing = true)
    public Firestore firestore() throws IOException {
        log.info("Initializing Firestore with project ID: {}", projectId);

        FirestoreOptions.Builder optionsBuilder = FirestoreOptions.newBuilder()
                .setProjectId(projectId);

        try {
            if (!credentialsPath.isEmpty()) {
                log.info("Using credentials from path: {}", credentialsPath);
                GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
                optionsBuilder.setCredentials(credentials);
            } else {
                log.info("Using default credentials (Application Default Credentials)");
                // For Cloud Run, this will automatically use the service account attached to the instance
            }

            Firestore firestore = optionsBuilder.build().getService();
            log.info("Firestore initialized successfully");

            return firestore;
        } catch (Exception e) {
            log.error("Failed to initialize Firestore: {}", e.getMessage());
            log.error("Please ensure:");
            log.error("1. The service account has the required Firestore permissions (roles/datastore.user, roles/datastore.viewer)");
            log.error("2. The Firestore API is enabled in the project");
            log.error("3. The project ID is correct: {}", projectId);
            log.error("4. For Cloud Run, ensure the service account is properly attached to the service");
            throw new RuntimeException("Firestore initialization failed", e);
        }
    }
}