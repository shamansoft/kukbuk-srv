package net.shamansoft.cookbook.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import jakarta.annotation.PreDestroy;
import java.io.IOException;

@Configuration
@Profile("local")
@ConditionalOnProperty(name = "firestore.emulator.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class LocalFirestoreConfig {

    @Value("${google.cloud.project-id:local-dev-project}")
    private String projectId;

    @Value("${firestore.emulator.ui.enabled:true}")
    private boolean uiEnabled;

    private FirestoreEmulatorContainer firestoreEmulator;

    @Bean
    @Primary
    public Firestore localFirestore() throws IOException {
        log.info("Starting Firestore emulator for local development...");
        
        // Create and start Firestore emulator container
        firestoreEmulator = new FirestoreEmulatorContainer(
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
        );
        
        firestoreEmulator.start();
        
        String emulatorEndpoint = firestoreEmulator.getEmulatorEndpoint();
        log.info("Firestore emulator started at: {}", emulatorEndpoint);
        
        // Set environment variable for other tools to connect
        System.setProperty("FIRESTORE_EMULATOR_HOST", "localhost:" + firestoreEmulator.getMappedPort(8080));
        
        // Configure Firestore client to use emulator
        Firestore firestore = FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setHost(emulatorEndpoint)
                .setCredentials(GoogleCredentials.newBuilder().build())
                .build()
                .getService();
        
        log.info("Firestore client configured for local development with project: {}", projectId);
        
        if (uiEnabled) {
            log.info("==============================================");
            log.info("🔥 FIRESTORE EMULATOR RUNNING");
            log.info("==============================================");
            log.info("Emulator endpoint: {}", emulatorEndpoint);
            log.info("Project ID: {}", projectId);
            log.info("");
            log.info("To inspect data with Firebase UI:");
            log.info("1. Run: ./scripts/firestore-ui.sh");
            log.info("2. Open: http://localhost:4000");
            log.info("");
            log.info("Alternative methods:");
            log.info("• Manual: export FIRESTORE_EMULATOR_HOST=localhost:{} && firebase emulators:ui", firestoreEmulator.getMappedPort(8080));
            log.info("• Docker: docker-compose -f docker-compose.local.yml up firestore-ui");
            log.info("• Direct access: http://localhost:{}", firestoreEmulator.getMappedPort(8080));
            log.info("==============================================");
        }
        
        return firestore;
    }

    @PreDestroy
    public void cleanup() {
        if (firestoreEmulator != null && firestoreEmulator.isRunning()) {
            log.info("Stopping Firestore emulator...");
            firestoreEmulator.stop();
        }
    }
}