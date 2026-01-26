package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class GoogleDrive {

    private final RestClient driveClient;
    private final RestClient uploadClient;

    public GoogleDrive(@Qualifier("driveRestClient") RestClient driveRestClient,
                       @Qualifier("uploadRestClient") RestClient uploadRestClient) {
        this.driveClient = driveRestClient;
        this.uploadClient = uploadRestClient;
    }

    @SuppressWarnings("unchecked")
    public Optional<Item> getFolder(String name, String authToken) {
        String query = "mimeType='application/vnd.google-apps.folder' and name='"
                + name.replace("'", "\\'")
                + "' and 'root' in parents and trashed=false";

        // Log token info for debugging (masked)
        String tokenInfo = authToken == null ? "null" :
                String.format("length=%d, starts=%s..., ends=...%s",
                        authToken.length(),
                        authToken.length() > 10 ? authToken.substring(0, 10) : authToken,
                        authToken.length() > 10 ? authToken.substring(authToken.length() - 10) : "");
        log.info("Searching for folder '{}' with query: {}, token: {}", name, query, tokenInfo);

        Map<String, Object> listResponse = driveClient.get()
                .uri(uri -> uri.path("/files")
                        .queryParam("q", query)
                        .queryParam("orderBy", "createdTime")
                        .queryParam("pageSize", "1000")
                        .queryParam("fields", "files(id,name,createdTime)")
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        var files = (java.util.List<Map<String, Object>>) listResponse.get("files");

        if (files == null || files.isEmpty()) {
            log.info("No folder found with name '{}' in root directory", name);
            return Optional.empty();
        }

        log.info("Found {} folder(s) with name '{}': {}", files.size(), name, files);

        String selectedId = files.getFirst().get("id").toString();
        log.info("Selected folder '{}' with ID: {} (first in ordered results)", name, selectedId);

        return Optional.of(new Item(selectedId, name));
    }

    @SuppressWarnings("unchecked")
    public Item createFolder(String name, String authToken) {
        log.warn("Creating NEW folder '{}' in root directory - this may indicate a duplicate folder issue!", name);

        Map<String, Object> metadata = Map.of(
                "name", name,
                "mimeType", "application/vnd.google-apps.folder"
        );

        log.debug("Folder metadata: {}", metadata);

        try {
            Map<String, Object> created = driveClient.post()
                    .uri(uri -> uri.path("/files").queryParam("fields", "id").build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(metadata)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (created == null || !created.containsKey("id")) {
                throw new ClientException("Unexpected response: " + created);
            }
            log.warn("SUCCESSFULLY created folder '{}' with ID: {} - this is a NEW folder in root!", name, created.get("id"));
            return new Item(created.get("id").toString(), name);
        } catch (Exception e) {
            log.error("Failed to create folder '{}': {}", name, e.getMessage(), e);
            if (e instanceof ClientException) {
                throw e;
            }
            throw new ClientException("Failed to create folder", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<Item> getFile(String name, String folderId, String authToken) {
        Map<String, Object> listResponse = driveClient.get()
                .uri(uri -> uri.path("/files")
                        .queryParam("q", "name='" + name.replace("'", "\\'")
                                + "' and '" + folderId + "' in parents and trashed=false")
                        .queryParam("fields", "files(id)")
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        var files = (java.util.List<Map<String, Object>>) listResponse.get("files");
        if (files != null && !files.isEmpty()) {
            return Optional.of(new Item(files.getFirst().get("id").toString(), name));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Item updateFile(Item file, String content, String authToken) {
        Map<String, Object> metadata = Map.of("name", file.name());
        try {
            driveClient.patch()
                    .uri(uri -> uri.path("/files/" + file.id()).build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(metadata)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> updateResponse = uploadClient.patch()
                    .uri(uri -> uri.path("/files/" + file.id())
                            .queryParam("uploadType", "media")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(new org.springframework.http.MediaType("application", "x-yaml", java.nio.charset.StandardCharsets.UTF_8))
                    .body(content)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (updateResponse == null || !updateResponse.containsKey("id")) {
                throw new ClientException("Failed to update Drive file");
            }
            log.info("Updated file '{}' with ID: {}", file.name(), updateResponse.get("id"));

            return file;
        } catch (Exception e) {
            log.error("Failed to update file", e);
            if (e instanceof ClientException) {
                throw e;
            }
            throw new ClientException("Failed to update file", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Item createFile(String name, String folderId, String content, String authToken) {
        try {
            Map<String, Object> metadataMap = Map.of(
                    "name", name,
                    "parents", java.util.Collections.singletonList(folderId),
                    "mimeType", "application/x-yaml"
            );

            log.debug("Creating new file '{}' in folder {}", name, folderId);

            // Create the file with proper metadata first
            Map<String, Object> createResponse = driveClient.post()
                    .uri(uri -> uri.path("/files")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(metadataMap)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            // Step 2: Update the content of the created file
            if (createResponse == null || !createResponse.containsKey("id")) {
                throw new RuntimeException("Failed to create Drive file");
            }

            final Item file = new Item(createResponse.get("id").toString(), name);
            log.debug("Created file {} metadata. Now uploading content...", file);

            // Use the upload API to add the content to the file
            Map<String, Object> updateResponse = uploadClient.patch()
                    .uri(uri -> uri.path("/files/" + file.id())
                            .queryParam("uploadType", "media")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/x-yaml"))
                    .body(content)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (updateResponse == null || !updateResponse.containsKey("id")) {
                throw new ClientException("Failed to upload content to Drive file");
            }
            log.info("File {} created successfully", file);
            return file;
        } catch (Exception e) {
            log.error("Failed to create file", e);
            if (e instanceof ClientException) {
                throw e;
            }
            throw new ClientException("Failed to create file", e);
        }
    }

    /**
     * List files in folder with pagination
     *
     * @param authToken User's OAuth access token
     * @param folderId  Google Drive folder ID
     * @param pageSize  Number of items per page (max 100)
     * @param pageToken Pagination token from previous response (null for first page)
     * @return DriveFileListResult with files and nextPageToken
     */
    @SuppressWarnings("unchecked")
    public DriveFileListResult listFiles(String authToken, String folderId,
                                         int pageSize, String pageToken) {
        log.debug("Listing files in folder: {}, pageSize: {}, pageToken: {}",
                folderId, pageSize, pageToken);

        String query = String.format("'%s' in parents and trashed=false and mimeType='application/x-yaml'",
                folderId);

        Map<String, Object> response = driveClient.get()
                .uri(uri -> {
                    var uriBuilder = uri.path("/files")
                            .queryParam("q", query)
                            .queryParam("pageSize", String.valueOf(Math.min(pageSize, 100)))
                            .queryParam("orderBy", "modifiedTime desc")
                            .queryParam("fields", "files(id,name,modifiedTime),nextPageToken");

                    if (pageToken != null) {
                        uriBuilder.queryParam("pageToken", pageToken);
                    }

                    return uriBuilder.build();
                })
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) {
            throw new ClientException("Failed to list files from Drive");
        }

        var filesData = (java.util.List<Map<String, Object>>) response.get("files");
        String nextPageToken = (String) response.get("nextPageToken");

        java.util.List<DriveFileInfo> files = filesData != null
                ? filesData.stream()
                .map(fileMap -> new DriveFileInfo(
                        (String) fileMap.get("id"),
                        (String) fileMap.get("name"),
                        (String) fileMap.get("modifiedTime")
                ))
                .toList()
                : java.util.Collections.emptyList();

        log.debug("Listed {} files from folder: {}", files.size(), folderId);

        return new DriveFileListResult(files, nextPageToken);
    }

    /**
     * Download file content as string (for YAML files).
     * Downloads as bytes and converts to UTF-8 string to ensure proper encoding.
     *
     * @param authToken User's OAuth access token
     * @param fileId    Google Drive file ID
     * @return File content as UTF-8 string
     */
    public String downloadFileAsString(String authToken, String fileId) {
        log.debug("Downloading file as string: {}", fileId);

        // Download as bytes to ensure proper encoding handling
        byte[] bytes = driveClient.get()
                .uri(uri -> uri.path("/files/" + fileId)
                        .queryParam("alt", "media")
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .body(byte[].class);

        if (bytes == null) {
            throw new ClientException("Failed to download file from Drive: " + fileId);
        }

        // Convert bytes to UTF-8 string explicitly to handle international characters
        String content = new String(bytes, StandardCharsets.UTF_8);

        log.debug("Downloaded file: {} ({} bytes)", fileId, content.length());
        return content;
    }

    /**
     * Download file content as bytes (for media files)
     *
     * @param authToken User's OAuth access token
     * @param fileId    Google Drive file ID
     * @return File content as byte array
     */
    public byte[] downloadFileAsBytes(String authToken, String fileId) {
        log.debug("Downloading file as bytes: {}", fileId);

        byte[] content = driveClient.get()
                .uri(uri -> uri.path("/files/" + fileId)
                        .queryParam("alt", "media")
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .body(byte[].class);

        if (content == null) {
            throw new ClientException("Failed to download file from Drive: " + fileId);
        }

        log.debug("Downloaded file: {} ({} bytes)", fileId, content.length);
        return content;
    }

    /**
     * Get file metadata
     *
     * @param authToken User's OAuth access token
     * @param fileId    Google Drive file ID
     * @return DriveFileMetadata with file details
     */
    @SuppressWarnings("unchecked")
    public DriveFileMetadata getFileMetadata(String authToken, String fileId) {
        log.debug("Getting file metadata: {}", fileId);

        Map<String, Object> metadata = driveClient.get()
                .uri(uri -> uri.path("/files/" + fileId)
                        .queryParam("fields", "id,name,mimeType,modifiedTime")
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (metadata == null) {
            throw new ClientException("Failed to get file metadata from Drive: " + fileId);
        }

        return new DriveFileMetadata(
                (String) metadata.get("id"),
                (String) metadata.get("name"),
                (String) metadata.get("mimeType"),
                (String) metadata.get("modifiedTime")
        );
    }

    public record Item(String id, String name) {
        public String url() {
            return "https://drive.google.com/file/d/" + id + "/view";
        }
    }

    public record DriveFileListResult(java.util.List<DriveFileInfo> files, String nextPageToken) {
    }

    public record DriveFileInfo(String id, String name, String modifiedTime) {
    }

    public record DriveFileMetadata(
            String id,
            String name,
            String mimeType,
            String modifiedTime
    ) {}
}
