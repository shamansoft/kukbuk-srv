package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;

/**
 * Provider-agnostic storage operation surface used by RecipeService / RecipeMediaProxyService.
 * Method names mirror {@link DriveService} so existing consumers change only which bean they call.
 */
public interface StorageProvider {

    StorageType type();

    String generateFileName(String title);

    /**
     * Ensure the recipe folder exists.
     *
     * @param accessToken provider access token
     * @param folderName  desired folder name (may be blank → provider default / app-folder root)
     * @return the folder reference (id may be a Drive id, or a Dropbox path such as "" or "/Sub")
     */
    FolderRef getOrCreateFolder(String accessToken, String folderName);

    DriveService.UploadResult uploadRecipeYaml(String accessToken, String folderId, String fileName, String content);

    GoogleDrive.DriveFileListResult listRecipeFiles(String accessToken, String folderId, int pageSize, String pageToken);

    String getFileContent(String accessToken, String fileId);

    byte[] downloadFile(String accessToken, String fileId);

    String getFileMimeType(String accessToken, String fileId);

    GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId);

    record FolderRef(String id, String name) {
    }
}
