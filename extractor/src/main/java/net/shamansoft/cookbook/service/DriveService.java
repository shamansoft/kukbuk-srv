package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;

public interface DriveService {

    /**
     * Generates a clean filename for the recipe YAML based on the title.
     *
     * @param title the recipe title
     * @return sanitized filename ending with .yaml
     */
    String generateFileName(String title);

    /**
     * Retrieves or creates the configured folder in the user's Google Drive.
     * <p>This is a stub; real implementation should use Drive HTTP API or client library.</p>
     *
     * @param authToken OAuth2 access token
     * @return ID of the Drive folder
     */
    String getOrCreateFolder(String authToken);

    /**
     * Uploads the recipe content as a YAML file to the specified Drive folder.
     * <p>This is a stub; real implementation should use Drive HTTP API or client library.</p>
     *
     * @param authToken OAuth2 access token
     * @param folderId  ID of the Drive folder
     * @param fileName  name of the file to create/update
     * @param content   YAML content of the recipe
     * @return Drive file ID of the uploaded or updated file
     */
    UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content);

    /**
     * Save recipe to Google Drive for a user
     * Gets credentials from StorageService and uploads the recipe
     *
     * @param userId Firebase user ID
     * @param content Recipe content (YAML)
     * @param title Recipe title (for filename)
     * @return UploadResult with file ID and URL
     * @throws Exception if storage not connected or upload fails
     */
    UploadResult saveRecipeForUser(String userId, String content, String title) throws Exception;

    /**
     * List recipe files from a Google Drive folder with pagination.
     *
     * @param authToken OAuth2 access token
     * @param folderId  Google Drive folder ID
     * @param pageSize  Number of items per page (max 100)
     * @param pageToken Pagination token from previous response (null for first page)
     * @return DriveFileListResult with files and nextPageToken
     */
    GoogleDrive.DriveFileListResult listRecipeFiles(String authToken, String folderId,
                                                    int pageSize, String pageToken);

    /**
     * Download file content as string (for YAML files).
     *
     * @param authToken OAuth2 access token
     * @param fileId    Google Drive file ID
     * @return File content as string
     */
    String getFileContent(String authToken, String fileId);

    /**
     * Download file content as bytes (for media files).
     *
     * @param authToken OAuth2 access token
     * @param fileId    Google Drive file ID
     * @return File content as bytes
     */
    byte[] downloadFile(String authToken, String fileId);

    /**
     * Get file MIME type.
     *
     * @param authToken OAuth2 access token
     * @param fileId    Google Drive file ID
     * @return MIME type string
     */
    String getFileMimeType(String authToken, String fileId);

    /**
     * Get file metadata including ID, name, MIME type, and modified time.
     *
     * @param authToken OAuth2 access token
     * @param fileId    Google Drive file ID
     * @return DriveFileMetadata with file details
     */
    GoogleDrive.DriveFileMetadata getFileMetadata(String authToken, String fileId);

    record UploadResult(String fileId, String fileUrl) {
    }
}
