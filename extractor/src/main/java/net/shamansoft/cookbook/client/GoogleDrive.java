package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class GoogleDrive {

    private static final ObjectMapper JSON = new ObjectMapper();

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
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

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
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
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
        String escapedName = name.replace("\\", "\\\\").replace("'", "\\'");
        String query = "name='" + escapedName + "' and '" + folderId + "' in parents and trashed=false";
        String pageToken = null;
        do {
            String currentPage = pageToken;
            Map<String, Object> listResponse = driveClient.get()
                    .uri(uri -> {
                        var builder = uri.path("/files")
                                .queryParam("q", "{query}")
                                .queryParam("fields", "files(id),nextPageToken,incompleteSearch");
                        if (currentPage != null) {
                            return builder.queryParam("pageToken", "{pageToken}").build(query, currentPage);
                        }
                        return builder.build(query);
                    })
                    .header("Authorization", "Bearer " + authToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (listResponse == null || Boolean.TRUE.equals(listResponse.get("incompleteSearch"))) {
                throw new ClientException("Could not determine whether the recipe exists in Drive");
            }
            var files = (List<Map<String, Object>>) listResponse.get("files");
            if (files != null && !files.isEmpty()) {
                return Optional.of(new Item(files.getFirst().get("id").toString(), name));
            }
            pageToken = (String) listResponse.get("nextPageToken");
        } while (pageToken != null && !pageToken.isBlank());
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
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.error("Failed to update file", e);
            throw new ClientException("Failed to update file", e);
        }
        updateFileContent(file.id(), content, authToken);
        log.info("Updated file '{}' with ID: {}", file.name(), file.id());
        return file;
    }

    /**
     * Replaces the content of an existing file, keeping its id, name and parents.
     */
    @SuppressWarnings("unchecked")
    public void updateFileContent(String fileId, String content, String authToken) {
        try {
            Map<String, Object> updateResponse = uploadClient.patch()
                    .uri(uri -> uri.path("/files/" + fileId)
                            .queryParam("uploadType", "media")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(new org.springframework.http.MediaType("application", "x-yaml", java.nio.charset.StandardCharsets.UTF_8))
                    .body(content)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (updateResponse == null || !updateResponse.containsKey("id")) {
                throw new ClientException("Failed to update Drive file");
            }
        } catch (Exception e) {
            log.error("Failed to update file content: {}", fileId, e);
            if (e instanceof ClientException) {
                throw e;
            }
            throw new ClientException("Failed to update file", e);
        }
    }

    public Item createFile(String name, String folderId, String content, String authToken) {
        try {
            // Send metadata and content together to avoid leaving a metadata-only file on failure.
            String boundary = UUID.randomUUID().toString();
            String delimiter = "--" + boundary;
            String metadata = JSON.writeValueAsString(Map.of(
                    "name", name, "parents", List.of(folderId), "mimeType", "application/x-yaml"));
            byte[] body = (delimiter + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                    + metadata + "\r\n" + delimiter + "\r\nContent-Type: application/x-yaml; charset=UTF-8\r\n\r\n"
                    + content + "\r\n" + delimiter + "--\r\n").getBytes(StandardCharsets.UTF_8);
            Map<String, Object> created = uploadClient.post()
                    .uri(uri -> uri.path("/files")
                            .queryParam("uploadType", "multipart")
                            .queryParam("fields", "id")
                            .build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(new MediaType("multipart", "related", Map.of("boundary", boundary)))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (created == null || !(created.get("id") instanceof String id) || id.isBlank()) {
                throw new ClientException("Failed to create Drive file: missing file ID");
            }
            return new Item(id, name);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
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
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

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

        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ClientException("Drive file contains invalid UTF-8: " + fileId, e);
        }
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
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

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
    ) {
    }
}
