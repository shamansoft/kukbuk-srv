package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.service.RecipeService;
import net.shamansoft.cookbook.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false")
public class CookbookController {

    private final StorageService storageService;
    private final RecipeService recipeService;

    @GetMapping("/")
    public String gcpHealth() {
        return "OK";
    }

    @GetMapping("/hello/{name}")
    public String index(@PathVariable("name") String name) {
        return "Hello, Cookbook user %s!".formatted(name);
    }

    /**
     * Get current user's profile (basic info from Firebase token plus storage status)
     */
    @GetMapping("/api/user/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Getting profile for user: {}", userId);

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", userId);
        profile.put("email", userEmail);

        // Add storage status (without exposing sensitive tokens)
        try {
            boolean hasStorage = storageService.isStorageConnected(userId);
            profile.put("hasStorageConfigured", hasStorage);

            if (hasStorage) {
                StorageInfo storage = storageService.getStorageInfo(userId);
                profile.put("storageType", storage.type().getFirestoreValue());
                profile.put("storageConnectedAt", storage.connectedAt());
                // Intentionally NOT including access/refresh tokens for security
            }
        } catch (Exception e) {
            log.debug("No storage configured for user {}: {}", userId, e.getMessage());
            profile.put("hasStorageConfigured", false);
        }

        return ResponseEntity.ok(profile);
    }

    /**
     * Save recipe endpoint
     * <p>
     * Authentication flow:
     * 1. Firebase ID token required in Authorization header (validates user)
     * 2. Backend retrieves storage OAuth tokens from Firestore (encrypted)
     * 3. Backend uses storage provider to save recipe
     * <p>
     * No OAuth tokens in headers - all managed server-side!
     */
    @PostMapping(
            path = "/recipe",
            consumes = "application/json",
            produces = "application/json"
    )
    public RecipeResponse createRecipe(@RequestBody @Valid Request request,
                                       @RequestParam(value = "compression", required = false) String compression,
                                       @RequestAttribute("userId") String userId,
                                       @RequestAttribute("userEmail") String userEmail
    )
            throws IOException, AuthenticationException {

        log.info("Creating recipe for user: {} ({})", userEmail, userId);
        log.info("Processing recipe request - URL: {}, Title: {}, Has HTML: {}, Compression: {}",
                request.url(),
                request.title(),
                request.html() != null && !request.html().isEmpty(),
                compression != null ? compression : "default");
        return recipeService.createRecipe(userId, request.url(), request.html(), compression, request.title());
    }
}