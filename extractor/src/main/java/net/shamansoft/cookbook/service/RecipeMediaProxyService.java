package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Proxies media files from the user's connected storage provider.
 * Validates the user session and uses their access token to fetch the file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeMediaProxyService {

    private final StorageProviderResolver storageProviderResolver;
    private final StorageService storageService;

    public MediaContent getMediaFile(String userId, String driveFileId) {
        log.info("Proxying media file: {} for user: {}", driveFileId, userId);

        StorageInfo storage = storageService.getStorageInfo(userId);
        StorageProvider provider = storageProviderResolver.resolve(storage.type());

        try {
            byte[] content = provider.downloadFile(storage.accessToken(), driveFileId);
            String mimeType = provider.getFileMimeType(storage.accessToken(), driveFileId);

            log.info("Successfully proxied media: {} ({} bytes, type: {})",
                    driveFileId, content.length, mimeType);

            return new MediaContent(content, mimeType);

        } catch (Exception e) {
            log.error("Failed to proxy media file: {}", driveFileId, e);

            if (e.getMessage() != null &&
                    (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
                throw new RecipeNotFoundException("Media file not found: " + driveFileId, e);
            }

            throw e;
        }
    }

    public record MediaContent(byte[] data, String mimeType) {
    }
}
