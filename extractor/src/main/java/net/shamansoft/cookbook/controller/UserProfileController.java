package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.UserProfileResponseDto;
import net.shamansoft.cookbook.dto.UserProfileUpdateRequest;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final UserProfileService userProfileService;

    /**
     * Get current user's profile (basic info from Firebase token plus storage status)
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getUserProfile(
            @RequestAttribute("userId") String userId) {
        log.info("Getting profile for user: {}", userId);
        var optionalProfile = userProfileService.getProfile(userId);
        return optionalProfile
                .map(userProfile -> ResponseEntity.ok(userProfile.toDto()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Create or update current user's profile
     *
     * @param request   Profile update data (all fields optional)
     * @param userId    User ID from authentication token
     * @param userEmail User email from authentication token
     * @return Updated profile
     */
    @PostMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> updateUserProfile(
            @Valid @RequestBody UserProfileUpdateRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Updating profile for user: {}", userId);
        try {
            // Update profile with provided fields
            UserProfile updatedProfile = userProfileService.updateProfile(
                    userId,
                    userEmail,
                    request.getDisplayName(),
                    request.getEmail()
            );
            return ResponseEntity.status(HttpStatus.OK).body(updatedProfile.toDto());

        } catch (Exception e) {
            log.error("Failed to update profile for user {}: {}", userId, e.getMessage(), e);
            // Return error response
            UserProfileResponseDto errorResponse = UserProfileResponseDto.builder()
                    .userId(userId)
                    .email(userEmail)
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}