package net.shamansoft.cookbook.service;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveSDKService implements DriveService {

    private final Drive drive;
    @Value("${cookbook.drive.folder-name:kukbuk}")
    private String folderName;

    @Override
    public String generateFileName(String title) {
        String base = (title == null || title.isBlank()) ? "recipe-" + System.currentTimeMillis() : title;
        String clean = base.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (clean.endsWith("-")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean + ".yaml";
    }

    @Override
    public String getOrCreateFolder(String authToken) {
        try {
            // Query to find folder with given name.
            String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName.replace("'", "\\'") + "' and trashed=false";
            FileList result = drive.files().list()
                    .setQ(query)
                    .setFields("files(id)")
                    .setOauthToken(authToken)
                    .execute();
            if (result.getFiles() != null && !result.getFiles().isEmpty()) {
                return result.getFiles().getFirst().getId();
            }
            // Create folder if it does not exist.
            File fileMetadata = new File();
            fileMetadata.setName(folderName);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");
            File folder = drive.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            return folder.getId();
        } catch (IOException e) {
            log.error("Failed to get or create folder", e);
            throw new RuntimeException("Failed to get or create folder", e);
        }
    }

    @Override
    public UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content) {
        try {
            // Query to search for existing file
            String query = "name='" + fileName.replace("'", "\\'") + "' and '" + folderId + "' in parents and trashed=false";
            FileList result = drive.files().list()
                    .setQ(query)
                    .setFields("files(id)")
                    .setOauthToken(authToken)
                    .execute();
            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            InputStreamContent mediaContent = new InputStreamContent("application/x-yaml",
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            File file;
            if (result.getFiles() != null && !result.getFiles().isEmpty()) {
                // File exists: update file
                String fileId = result.getFiles().getFirst().getId();
                file = drive.files().update(fileId, null, mediaContent)
                        .setFields("id")
                        .setOauthToken(authToken)
                        .execute();
            } else {
                // File does not exist: set parent folder and create file
                fileMetadata.setParents(Collections.singletonList(folderId));
                file = drive.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .setOauthToken(authToken)
                        .execute();
            }
            return new UploadResult(file.getId(), getFileUrl(file.getId()));
        } catch (IOException e) {
            log.error("File upload/update failed", e);
            throw new RuntimeException("Failed to upload recipe to Drive", e);
        }
    }

    public String getFileUrl(String fileId) {
        return "https://drive.google.com/file/d/" + fileId + "/view?usp=sharing";
    }
}