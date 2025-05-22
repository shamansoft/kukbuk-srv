package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class GoogleDrive {

    private final WebClient driveClient;
    private final WebClient uploadClient;

    public GoogleDrive(@Value("${cookbook.drive.upload-url}") String uploadUrl,
                       @Value("${cookbook.drive.base-url}") String baseUrl) {
        this.driveClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.uploadClient = WebClient.builder()
                .baseUrl(uploadUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Optional<Item> getFolder(String name, String authToken) {
        Map<String, Object> listResponse = driveClient.get()
                .uri(uri -> uri.path("/files")
                        .queryParam("q", "mimeType='application/vnd.google-apps.folder' and name='"
                                + name.replace("'", "\\'")
                                + "' and trashed=false")
                        .queryParam("fields", "files(id)")
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        var files = (java.util.List<Map<String, Object>>) listResponse.get("files");
        if (files != null && !files.isEmpty()) {
            return Optional.of(new Item(files.getFirst().get("id").toString(), name));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Item createFolder(String name, String authToken) {
        Map<String, Object> metadata = Map.of(
                "name", name,
                "mimeType", "application/vnd.google-apps.folder"
        );
        try {
            Map<String, Object> created = driveClient.post()
                    .uri(uri -> uri.path("/files").queryParam("fields", "id").build())
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(metadata)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (created == null || !created.containsKey("id")) {
                throw new ClientException("Unexpected response: " + created);
            }
            log.info("Created folder '{}' with ID: {}", name, created.get("id"));
            return new Item(created.get("id").toString(), name);
        } catch (Exception e) {
            log.error("Failed to create folder", e);
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
                .bodyToMono(Map.class)
                .block();
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
                    .bodyValue(metadata)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            Map<String, Object> updateResponse = uploadClient.patch()
                    .uri(uri -> uri.path("/files/" + file.id())
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
                    .bodyValue(metadataMap)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
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
                    .bodyValue(content)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
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

    public record Item(String id, String name) {
        public String url() {
            return "https://drive.google.com/file/d/" + id + "/view";
        }
    }
}
