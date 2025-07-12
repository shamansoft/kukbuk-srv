package net.shamansoft.cookbook.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.repository.RecipeRepository;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Endpoint(id = "firestore")
@Profile("local")
@ConditionalOnProperty(name = "firestore.emulator.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class FirestoreEmulatorEndpoint {

    private final RecipeRepository recipeRepository;

    @ReadOperation
    public Map<String, Object> firestoreStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Get count of stored recipes
            CompletableFuture<Long> countFuture = recipeRepository.count();
            Long recipeCount = countFuture.join();
            
            status.put("status", "running");
            status.put("emulator", true);
            status.put("project", "local-dev-project");
            status.put("recipes_stored", recipeCount);
            status.put("ui_instructions", Map.of(
                "firebase_cli", "firebase emulators:ui --only firestore",
                "docker_compose", "docker-compose -f docker-compose.local.yml up firestore-ui",
                "url", "http://localhost:4000"
            ));
            
            // Add emulator environment variable info
            String emulatorHost = System.getProperty("FIRESTORE_EMULATOR_HOST");
            if (emulatorHost != null) {
                status.put("emulator_host", emulatorHost);
            }
            
        } catch (Exception e) {
            log.error("Error getting Firestore status", e);
            status.put("status", "error");
            status.put("error", e.getMessage());
        }
        
        return status;
    }
}