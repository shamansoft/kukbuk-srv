package net.shamansoft.cookbook.controller;

import com.google.cloud.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageDto;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.UserProfileResponseDto;
import net.shamansoft.cookbook.service.RecipeService;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false")
@RequestMapping("/v1/user")
public class UserProfileController {

    private final StorageService storageService;
    private final RecipeService recipeService;
    private final UserProfileService userProfileService;

    /**
     * Get current user's profile (basic info from Firebase token plus storage status)
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getUserProfile(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Getting profile for user: {}", userId);

        // Get user profile from Firestore to retrieve createdAt
        Instant createdAt;
        try {
            Map<String, Object> profileData = userProfileService.getOrCreateProfile(userId, userEmail);
            Timestamp firestoreTimestamp = (Timestamp) profileData.get("createdAt");
            createdAt = Instant.ofEpochSecond(firestoreTimestamp.getSeconds(), firestoreTimestamp.getNanos());
        } catch (Exception e) {
            log.error("Failed to get profile for user {}: {}", userId, e.getMessage(), e);
            // Fallback to current time if profile retrieval fails
            createdAt = Instant.now();
        }

        // Add storage status (without exposing sensitive tokens)
        StorageDto storageDto = null;
        try {
            boolean hasStorage = storageService.isStorageConnected(userId);
            if (hasStorage) {
                StorageInfo storage = storageService.getStorageInfo(userId);
                storageDto = StorageDto.builder()
                        .type(storage.type().getFirestoreValue())
                        .folderId(storage.folderId())
                        .folderName(storage.folderName())
                        .connectedAt(storage.connectedAt())
                        .build();
                // Intentionally NOT including access/refresh tokens for security
            }
        } catch (Exception e) {
            log.debug("No storage configured for user {}: {}", userId, e.getMessage());
            // storageDto remains null
        }

        UserProfileResponseDto response = UserProfileResponseDto.builder()
                .userId(userId)
                .email(userEmail)
                .createdAt(createdAt)
                .storage(storageDto)
                .build();

        return ResponseEntity.ok(response);
    }
}