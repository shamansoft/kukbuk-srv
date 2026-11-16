package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;

import java.util.Optional;

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

    /**
     * Content of the recipe file with this name in the folder, if one exists (used to keep user data on re-save).
     */
    Optional<String> findRecipeYaml(String accessToken, String folderId, String fileName);

    /**
     * Replace the content of an existing file addressed by id, keeping its id and name.
     */
    void updateFileContent(String accessToken, String fileId, String content);

    record FolderRef(String id, String name) {
    }
}
