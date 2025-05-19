package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Service for Google Drive operations related to the Cookbook application.
 */
@Slf4j
@Service
public class GoogleDriveRestService implements DriveService {
    private final WebClient driveClient;
    private final WebClient uploadClient;
    private final String folderName;

    /**
     * Constructs the service with configuration from application properties.
     *
     * @param folderName the name of the Drive folder (property: cookbook.drive.folder-name)
     */
    @Autowired
    public GoogleDriveRestService(@Value("${cookbook.drive.folder-name:kukbuk}") String folderName,
                                  @Value("${cookbook.drive.upload-url}") String uploadUrl,
                                  @Value("${cookbook.drive.base-url}") String baseUrl) {
        this.folderName = folderName;

        this.driveClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.uploadClient = WebClient.builder()
                .baseUrl(uploadUrl)
                .build();
    }

    /**
     * Constructor for testing, allowing injection of custom WebClients.
     *
     * @param folderName   Drive folder name
     * @param driveClient  WebClient for Drive REST calls
     * @param uploadClient WebClient for Drive upload calls
     */
    GoogleDriveRestService(String folderName,
                           WebClient driveClient,
                           WebClient uploadClient) {
        this.folderName = folderName;
        this.driveClient = driveClient;
        this.uploadClient = uploadClient;
    }


    /**
     * Retrieves or creates the configured folder in the user's Google Drive.
     * <p>This is a stub; real implementation should use Drive HTTP API or client library.</p>
     *
     * @param authToken OAuth2 access token
     * @return ID of the Drive folder
     */
    @SuppressWarnings("unchecked")
    @Override
    public String getOrCreateFolder(String authToken) {
        try {
            // Search for existing folder
            Map<String, Object> listResponse = driveClient.get()
                    .uri(uri -> uri.path("/files")
                            .queryParam("q", "mimeType='application/vnd.google-apps.folder' and name='"
                                    + folderName.replace("'", "\\'")
                                    + "' and trashed=false")
                            .queryParam("fields", "files(id)")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            var files = (java.util.List<Map<String, Object>>) listResponse.get("files");
            if (files != null && !files.isEmpty()) {
                return files.getFirst().get("id").toString();
            }
            // Create folder
            Map<String, Object> metadata = Map.of(
                    "name", folderName,
                    "mimeType", "application/vnd.google-apps.folder"
            );
            Map<String, Object> created = driveClient.post()
                    .uri(uri -> uri.path("/files").queryParam("fields", "id").build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(metadata)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (created == null || !created.containsKey("id")) {
                throw new RuntimeException("Failed to create Drive folder");
            }
            log.info("Created folder '{}' with ID: {}", folderName, created.get("id"));
            return created.get("id").toString();
        } catch (WebClientResponseException e) {
            log.error("Drive folder lookup/creation failed: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get or create Drive folder", e);
        }
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
    @SuppressWarnings("unchecked")
    @Override
    public UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content) {
        try {
            // Check for existing file
            Map<String, Object> listResponse = driveClient.get()
                    .uri(uri -> uri.path("/files")
                            .queryParam("q", "name='" + fileName.replace("'", "\\'")
                                    + "' and '" + folderId + "' in parents and trashed=false")
                            .queryParam("fields", "files(id)")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            var files = (java.util.List<Map<String, Object>>) listResponse.get("files");
            if (files != null && !files.isEmpty()) {
                String existingId = files.get(0).get("id").toString();
                
                // First ensure the metadata is correct (especially the name)
                Map<String, Object> metadata = Map.of(
                        "name", fileName
                );
            
                log.debug("Updating file metadata for ID: {}, setting name: {}", existingId, fileName);
                try {
                    driveClient.patch()
                            .uri(uri -> uri.path("/files/" + existingId)
                                    .build())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .bodyValue(metadata)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();
                } catch (WebClientResponseException e) {
                    log.warn("Failed to update file metadata: {}", e.getResponseBodyAsString());
                }
                
                // Then update the content
                Map<String, Object> updateResponse = uploadClient.patch()
                        .uri(uri -> uri.path("/files/" + existingId)
                                .queryParam("uploadType", "media")
                                .queryParam("fields", "id")
                                .build())
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(org.springframework.http.MediaType.parseMediaType("application/x-yaml"))
                        .bodyValue(content)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                if (updateResponse == null || !updateResponse.containsKey("id")) {
                    throw new RuntimeException("Failed to update Drive file");
                }
                String fileId = updateResponse.get("id").toString();
                log.info("File updated successfully with ID {} and name {}", fileId, fileName);
                return new UploadResult(fileId, getFileUrl(fileId));
            }
            // For creating new files, we'll use the two-step process
            // Step 1: Create the file metadata with proper name and parent folder
            Map<String, Object> metadataMap = Map.of(
                    "name", fileName,
                    "parents", java.util.Collections.singletonList(folderId),
                    "mimeType", "application/x-yaml"
            );
            
            log.debug("Creating new file '{}' in folder {}", fileName, folderId);
            
            // Create the file with proper metadata first
            Map<String, Object> createResponse = driveClient.post()
                    .uri(uri -> uri.path("/files")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(metadataMap)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            // Step 2: Update the content of the created file
            if (createResponse == null || !createResponse.containsKey("id")) {
                throw new RuntimeException("Failed to create Drive file");
            }
            
            final String fileId = createResponse.get("id").toString();
            log.debug("File created with ID: {}. Now uploading content...", fileId);
            
            // Use the upload API to add the content to the file
            Map<String, Object> updateResponse = uploadClient.patch()
                    .uri(uri -> uri.path("/files/" + fileId)
                            .queryParam("uploadType", "media")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/x-yaml"))
                    .bodyValue(content)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            // Verify the update was successful
            if (updateResponse == null || !updateResponse.containsKey("id")) {
                log.warn("Content update response missing ID field for file: {}", fileId);
            }
            log.info("File created successfully with ID {} and name {} in folder {}", fileId, fileName, folderId);
            return new UploadResult(fileId, getFileUrl(fileId));
        } catch (WebClientResponseException e) {
            log.error("Drive file upload/update failed: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to upload recipe to Drive", e);
        }
    }

    /**
     * Constructs the URL for the file in Google Drive given its ID.
     *
     * @param fileId Drive file ID
     * @return URL to view the file in Drive
     */
    public String getFileUrl(String fileId) {
        return "https://drive.google.com/file/d/" + fileId + "/view?usp=sharing";
    }
}