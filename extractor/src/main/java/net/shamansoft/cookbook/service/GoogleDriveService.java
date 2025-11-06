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
    private final Transliterator transliterator;

    @Autowired
    public GoogleDriveService(@Value("${cookbook.drive.folder-name:kukbuk}") String folderName,
                              GoogleDrive drive, Transliterator transliterator) {
        this.folderName = folderName;
        this.drive = drive;
        this.transliterator = transliterator;
    }

    @Override
    public String generateFileName(String title) {
        String lowerAscii = transliterator.toAsciiKebab(title);
        String base = lowerAscii.isEmpty() ? defaultName() : lowerAscii;
        return base + ".yaml";
    }

    private static String defaultName() {
        return "recipe-" + System.currentTimeMillis();
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
    public UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content) {
        var file = drive.getFile(fileName, folderId, authToken)
                .map(existingFile -> drive.updateFile(existingFile, content, authToken))
                .orElseGet(() -> drive.createFile(fileName, folderId, content, authToken));
        return new UploadResult(file.id(), file.url());
    }
}