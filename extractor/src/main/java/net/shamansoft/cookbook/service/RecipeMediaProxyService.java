package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Service for proxying media files from Google Drive.
 * Validates user session and fetches media using user's OAuth token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeMediaProxyService {
    private final DriveService googleDriveService;
    private final StorageService storageService;

    /**
     * Get media file from Google Drive.
     * Validates user session and uses their OAuth token to fetch the file.
     *
     * @param userId      Firebase user ID
     * @param driveFileId Google Drive file ID
     * @return MediaContent with file data and MIME type
     * @throws net.shamansoft.cookbook.exception.StorageNotConnectedException if storage not connected
     * @throws RecipeNotFoundException                                        if file not found
     */
    public MediaContent getMediaFile(String userId, String driveFileId) {
        log.info("Proxying media file: {} for user: {}", driveFileId, userId);

        // Get user's OAuth token (validates storage connected)
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.type() != StorageType.GOOGLE_DRIVE) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.type());
        }

        try {
            // Fetch file from Drive using user's token
            byte[] content = googleDriveService.downloadFile(
                    storage.accessToken(), driveFileId);

            String mimeType = googleDriveService.getFileMimeType(
                    storage.accessToken(), driveFileId);

            log.info("Successfully proxied media: {} ({} bytes, type: {})",
                    driveFileId, content.length, mimeType);

            return new MediaContent(content, mimeType);

        } catch (Exception e) {
            log.error("Failed to proxy media file: {}", driveFileId, e);

            // Check if it's a "not found" error from Drive API
            if (e.getMessage() != null &&
                    (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
                throw new RecipeNotFoundException("Media file not found: " + driveFileId, e);
            }

            throw e;
        }
    }

    /**
     * Media content with data and MIME type.
     *
     * @param data     File content as bytes
     * @param mimeType MIME type (e.g., "image/jpeg", "image/png")
     */
    public record MediaContent(byte[] data, String mimeType) {
    }
}
