package net.shamansoft.cookbook.service;

public interface DriveService {

    record UploadResult(String fileId, String fileUrl) {
    }

    String generateFileName(String title);

    String getOrCreateFolder(String authToken);

    UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content);
}
