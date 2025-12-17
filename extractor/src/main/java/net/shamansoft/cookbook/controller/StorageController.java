package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageConnectionRequest;
import net.shamansoft.cookbook.dto.StorageConnectionResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageStatusResponse;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Google Drive storage management.
 * <p>
 * All endpoints require Firebase authentication - userId and userEmail
 * are injected by FirebaseAuthFilter.
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(
        originPatterns = "chrome-extension://*",
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false"
)
public class StorageController {

    private final StorageService storageService;

    /**
     * Connect Google Drive storage for the authenticated user.
     *
     * @param userId  Injected by FirebaseAuthFilter
     * @param request OAuth tokens and configuration
     * @return Connection response with success/failure status
     */
    @PostMapping("/google-drive/connect")
    public ResponseEntity<StorageConnectionResponse> connectGoogleDrive(
            @RequestAttribute("userId") String userId,
            @RequestBody @Valid StorageConnectionRequest request) {

        log.info("Connecting Google Drive storage for user: {}", userId);

        storageService.connectGoogleDrive(
                userId,
                request.getAccessToken(),
                request.getRefreshToken(),
                request.getExpiresIn(),
                request.getDefaultFolderId()
        );

        log.info("Google Drive connected successfully for user: {}", userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(StorageConnectionResponse.success(
                        "Google Drive connected successfully",
                        true
                ));
    }

    /**
     * Disconnect Google Drive storage for the authenticated user.
     * Removes all stored tokens and configuration.
     *
     * @param userId Injected by FirebaseAuthFilter
     * @return Disconnection response
     */
    @DeleteMapping("/google-drive/disconnect")
    public ResponseEntity<StorageConnectionResponse> disconnectGoogleDrive(
            @RequestAttribute("userId") String userId) {

        log.info("Disconnecting Google Drive storage for user: {}", userId);

        storageService.disconnectStorage(userId);

        log.info("Google Drive disconnected successfully for user: {}", userId);

        return ResponseEntity.ok(
                StorageConnectionResponse.success(
                        "Google Drive disconnected successfully",
                        false
                )
        );
    }

    /**
     * Get Google Drive connection status for the authenticated user.
     * Returns metadata only - no sensitive tokens.
     *
     * @param userId Injected by FirebaseAuthFilter
     * @return Storage status including connection state and metadata
     */
    @GetMapping("/google-drive/status")
    public ResponseEntity<StorageStatusResponse> getGoogleDriveStatus(
            @RequestAttribute("userId") String userId) {

        log.debug("Getting Google Drive status for user: {}", userId);

        try {
            StorageInfo info = storageService.getStorageInfo(userId);
            return ResponseEntity.ok(StorageStatusResponse.fromStorageInfo(info));

        } catch (StorageNotConnectedException e) {
            log.debug("Storage not connected for user: {}", userId);
            return ResponseEntity.ok(StorageStatusResponse.notConnected());
        }
    }
}
