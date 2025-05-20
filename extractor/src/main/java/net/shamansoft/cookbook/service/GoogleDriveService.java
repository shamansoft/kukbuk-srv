package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for Google Drive operations related to the Cookbook application.
 */
@Slf4j
@Service
public class GoogleDriveService implements DriveService {
    private final String folderName;
    private final GoogleDrive drive;

    /**
     * Constructs the service with configuration from application properties.
     *
     * @param folderName the name of the Drive folder (property: cookbook.drive.folder-name)
     */
    @Autowired
    public GoogleDriveService(@Value("${cookbook.drive.folder-name:kukbuk}") String folderName,
                              GoogleDrive drive) {
        this.folderName = folderName;
        this.drive = drive;
    }

    /**
     * Retrieves or creates the configured folder in the user's Google Drive.
     * <p>This is a stub; real implementation should use Drive HTTP API or client library.</p>
     *
     * @param authToken OAuth2 access token
     * @return ID of the Drive folder
     */
    @Override
    public String getOrCreateFolder(String authToken) {
        return drive.getFolder(folderName, authToken)
                .orElseGet(() -> drive.createFolder(folderName, authToken)).id();
    }

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
    @Override
    public UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content) {
        var file = drive.getFile(fileName, folderId, authToken)
                .map(existingFile -> drive.updateFile(existingFile, content, authToken))
                .orElseGet(() -> drive.createFile(fileName, folderId, content, authToken));
        return new UploadResult(file.id(), file.url());
    }
}