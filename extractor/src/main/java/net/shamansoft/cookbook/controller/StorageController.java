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
@RequestMapping("/v1/storage")
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
     * Exchanges authorization code for OAuth tokens server-side, and creates/finds Google Drive folder.
     *
     * @param userId  Injected by FirebaseAuthFilter
     * @param request Authorization code, redirect URI, and folder name from mobile app
     * @return Connection response with folder information
     */
    @PostMapping("/google-drive/connect")
    public ResponseEntity<StorageConnectionResponse> connectGoogleDrive(
            @RequestAttribute("userId") String userId,
            @RequestBody @Valid StorageConnectionRequest request) {

        log.info("Connecting Google Drive storage for user: {}", userId);

        try {
            StorageService.FolderInfo folderInfo = storageService.connectGoogleDrive(
                    userId,
                    request.getAuthorizationCode(),
                    request.getRedirectUri(),
                    request.getFolderName()
            );

            log.info("Google Drive connected successfully for user: {} with folder: {} ({})",
                    userId, folderInfo.folderName(), folderInfo.folderId());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(StorageConnectionResponse.success(
                            "Google Drive connected successfully",
                            true,
                            folderInfo.folderId(),
                            folderInfo.folderName()
                    ));

        } catch (IllegalArgumentException e) {
            log.error("Invalid authorization code for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(StorageConnectionResponse.error(
                            "Invalid authorization code: " + e.getMessage()
                    ));

        } catch (IllegalStateException e) {
            log.error("OAuth configuration error for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(StorageConnectionResponse.error(
                            "OAuth error: " + e.getMessage()
                    ));
        }
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
