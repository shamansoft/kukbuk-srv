package net.shamansoft.cookbook.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.service.RecipeMediaProxyService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * REST controller for media proxy endpoints.
 * Proxies media files from Google Drive to mobile app.
 * <p>
 * All endpoints require Firebase authentication - userId is injected by FirebaseAuthFilter.
 */
@RestController
@RequestMapping("/v1/media")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(
        originPatterns = "*",  // Configure for mobile app (restrict in production)
        allowedHeaders = "*",
        exposedHeaders = {"Cache-Control", "Content-Type"},
        allowCredentials = "true"
)
public class MediaController {

    private final RecipeMediaProxyService mediaProxyService;

    /**
     * GET /v1/media/{driveFileId} - Proxy media from Google Drive.
     * <p>
     * Validates user session, fetches media using user's OAuth token,
     * and streams bytes back with correct Content-Type.
     * <p>
     * This endpoint is for Drive-hosted images only. External image URLs
     * (from original recipe sites) should be loaded directly by mobile app.
     *
     * @param userId      Firebase user ID (injected by FirebaseAuthFilter)
     * @param driveFileId Google Drive file ID
     * @return Media bytes with appropriate Content-Type header
     * <p>
     * HTTP Status Codes:
     * - 200 OK: Success (with media bytes)
     * - 401 Unauthorized: No Firebase token
     * - 404 Not Found: File not found
     * - 428 Precondition Required: Storage not connected
     */
    @GetMapping("/{driveFileId}")
    public ResponseEntity<byte[]> getMedia(
            @RequestAttribute("userId") String userId,
            @PathVariable String driveFileId) {

        log.info("Proxying media: {} for user: {}", driveFileId, userId);

        RecipeMediaProxyService.MediaContent media = mediaProxyService.getMediaFile(
                userId, driveFileId);

        log.info("Successfully proxied media: {} ({} bytes, type: {})",
                driveFileId, media.data().length, media.mimeType());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.mimeType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(media.data());
    }
}
