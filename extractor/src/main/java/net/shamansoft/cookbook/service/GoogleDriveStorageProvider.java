package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * {@link StorageProvider} backed by Google Drive. Delegates all file operations to the
 * existing {@link GoogleDriveService} bean (single source of truth for Drive logic).
 */
@Service
public class GoogleDriveStorageProvider implements StorageProvider {

    private final GoogleDriveService googleDriveService;

    @Value("${cookbook.drive.folder-name}")
    private String folderName;

    public GoogleDriveStorageProvider(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @Override
    public StorageType type() {
        return StorageType.GOOGLE_DRIVE;
    }

    @Override
    public String generateFileName(String title) {
        return googleDriveService.generateFileName(title);
    }

    @Override
    public FolderRef getOrCreateFolder(String accessToken, String folderName) {
        String id = googleDriveService.getOrCreateFolder(accessToken);
        return new FolderRef(id, this.folderName);
    }

    @Override
    public DriveService.UploadResult uploadRecipeYaml(String accessToken, String folderId, String fileName, String content) {
        return googleDriveService.uploadRecipeYaml(accessToken, folderId, fileName, content);
    }

    @Override
    public GoogleDrive.DriveFileListResult listRecipeFiles(String accessToken, String folderId, int pageSize, String pageToken) {
        return googleDriveService.listRecipeFiles(accessToken, folderId, pageSize, pageToken);
    }

    @Override
    public String getFileContent(String accessToken, String fileId) {
        return googleDriveService.getFileContent(accessToken, fileId);
    }

    @Override
    public byte[] downloadFile(String accessToken, String fileId) {
        return googleDriveService.downloadFile(accessToken, fileId);
    }

    @Override
    public String getFileMimeType(String accessToken, String fileId) {
        return googleDriveService.getFileMimeType(accessToken, fileId);
    }

    @Override
    public GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        return googleDriveService.getFileMetadata(accessToken, fileId);
    }
}
