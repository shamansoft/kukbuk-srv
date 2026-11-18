package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.repository.DriveRecipeWriteLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Service for Google Drive operations related to the Cookbook application.
 */
@Slf4j
@Service
public class GoogleDriveService implements DriveService {
    private final String folderName;
    private final GoogleDrive drive;
    private final Transliterator transliterator;
    private final StorageService storageService;
    private final DriveRecipeWriteLock writeLock;

    @Autowired
    public GoogleDriveService(@Value("${cookbook.drive.folder-name}") String folderName,
                              GoogleDrive drive,
                              Transliterator transliterator,
                              StorageService storageService,
                              DriveRecipeWriteLock writeLock) {
        this.folderName = folderName;
        this.drive = drive;
        this.transliterator = transliterator;
        this.storageService = storageService;
        this.writeLock = writeLock;
    }

    private static String defaultName() {
        return "recipe-" + System.currentTimeMillis();
    }

    @Override
    public String generateFileName(String title) {
        String lowerAscii = transliterator.toAsciiKebab(title);
        String base = lowerAscii.isEmpty() ? defaultName() : lowerAscii;
        return base + ".yaml";
    }

    @Override
    public String getOrCreateFolder(String authToken) {
        log.info("Getting or creating folder: '{}'", folderName);

        var existingFolder = drive.getFolder(folderName, authToken);

        if (existingFolder.isPresent()) {
            String folderId = existingFolder.get().id();
            log.info("Using existing folder '{}' with ID: {}", folderName, folderId);
            return folderId;
        }

        log.warn("Folder '{}' not found - will create a new one", folderName);
        var newFolder = drive.createFolder(folderName, authToken);
        log.info("Created and will use new folder '{}' with ID: {}", folderName, newFolder.id());
        return newFolder.id();
    }

    @Override
    public UploadResult saveRecipeYaml(String authToken, String folderId, String fileName,
                                       Function<Optional<String>, String> edit) {
        try (var nameLock = writeLock.forName(folderId, fileName)) {
            Optional<GoogleDrive.Item> existing = drive.getFile(fileName, folderId, authToken);
            if (existing.isPresent()) {
                GoogleDrive.Item file = existing.get();
                editRecipeYaml(authToken, file.id(), yaml -> edit.apply(Optional.of(yaml)));
                return new UploadResult(file.id(), file.url());
            }
            String yaml = edit.apply(Optional.empty());
            GoogleDrive.Item created = nameLock.write(() -> drive.createFile(fileName, folderId, yaml, authToken));
            return new UploadResult(created.id(), created.url());
        }
    }

    @Override
    public UploadResult saveRecipeForUser(String userId, String content, String title) throws Exception {
        log.info("Saving recipe to Google Drive for user: {}", userId);

        // Get storage info (throws StorageNotConnectedException if not connected)
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.type() != StorageType.GOOGLE_DRIVE) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.type());
        }

        // Validate folder is configured
        if (storage.folderId() == null) {
            throw new StorageNotConnectedException(
                    "No folder configured for recipe storage. Please reconnect Google Drive or configure a folder.");
        }

        String folderId = storage.folderId();
        log.debug("Using folder ID: {}", folderId);

        // Generate filename and upload
        String fileName = generateFileName(title);
        UploadResult result = saveRecipeYaml(storage.accessToken(), folderId, fileName, existing -> content);

        log.info("Recipe saved to Google Drive: {}", result.fileUrl());
        return result;
    }

    /**
     * List recipe files from folder with pagination
     *
     * @param authToken User's OAuth access token
     * @param folderId  Folder ID to list from
     * @param pageSize  Number of items per page
     * @param pageToken Pagination token (null for first page)
     * @return DriveFileListResult with files and nextPageToken
     */
    public GoogleDrive.DriveFileListResult listRecipeFiles(String authToken, String folderId,
                                                           int pageSize, String pageToken) {
        log.info("Listing recipe files from folder: {}, pageSize: {}", folderId, pageSize);
        return drive.listFiles(authToken, folderId, pageSize, pageToken);
    }

    /**
     * Get file content as string (for YAML files)
     *
     * @param authToken User's OAuth access token
     * @param fileId    File ID
     * @return File content as string
     */
    public String getFileContent(String authToken, String fileId) {
        log.info("Getting file content: {}", fileId);
        return drive.downloadFileAsString(authToken, fileId);
    }

    /**
     * Download file as bytes (for media files)
     *
     * @param authToken User's OAuth access token
     * @param fileId    File ID
     * @return File content as bytes
     */
    public byte[] downloadFile(String authToken, String fileId) {
        log.info("Downloading file: {}", fileId);
        return drive.downloadFileAsBytes(authToken, fileId);
    }

    /**
     * Get file MIME type
     *
     * @param authToken User's OAuth access token
     * @param fileId    File ID
     * @return MIME type string
     */
    public String getFileMimeType(String authToken, String fileId) {
        GoogleDrive.DriveFileMetadata metadata = drive.getFileMetadata(authToken, fileId);
        return metadata.mimeType();
    }

    /**
     * Get file metadata
     *
     * @param authToken User's OAuth access token
     * @param fileId    File ID
     * @return DriveFileMetadata
     */
    public GoogleDrive.DriveFileMetadata getFileMetadata(String authToken, String fileId) {
        log.info("Getting file metadata: {}", fileId);
        return drive.getFileMetadata(authToken, fileId);
    }

    @Override
    public String editRecipeYaml(String authToken, String fileId, UnaryOperator<String> edit) {
        try (var fileLock = writeLock.forFile(fileId)) {
            String yaml = edit.apply(drive.downloadFileAsString(authToken, fileId));
            return fileLock.write(() -> {
                drive.updateFileContent(fileId, yaml, authToken);
                return yaml;
            });
        }
    }
}
