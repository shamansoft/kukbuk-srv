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
        
        if (!credentialsPath.isEmpty()) {
            log.info("Using credentials from path: {}", credentialsPath);
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
            optionsBuilder.setCredentials(credentials);
        } else {
            log.info("Using default credentials (Application Default Credentials)");
        }
        
        Firestore firestore = optionsBuilder.build().getService();
        log.info("Firestore initialized successfully");
        
        return firestore;
    }
}