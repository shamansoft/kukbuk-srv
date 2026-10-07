package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * HTTP client for the Dropbox file API (App-folder access).
 * All {@code path} arguments are relative to the app folder; {@code ""} is the app-folder root.
 */
@Slf4j
@Service
public class DropboxClient {

    private static final String API = "https://api.dropboxapi.com";
    private static final String CONTENT = "https://content.dropboxapi.com";
    private static final int MAX_RATE_LIMIT_RETRIES = 2;
    private static final long MAX_RETRY_AFTER_SECONDS = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    Sleeper sleeper = Thread::sleep;

    public DropboxClient(@Qualifier("genericRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a folder; a 409 {@code path/conflict/folder} is treated as "already exists".
     * Every other 409 (a file at that path, malformed path, no space, ...) is a real failure.
     */
    public void createFolder(String path, String token) {
        try {
            rpc(API + "/2/files/create_folder_v2", Map.of("path", path, "autorename", false), token);
            log.info("Created Dropbox folder: {}", path);
        } catch (RestClientResponseException e) {
            if (isEndpointError(e, "path/conflict/folder")) {
                log.info("Dropbox folder already exists: {}", path);
                return;
            }
            throw new ClientException("Failed to create Dropbox folder: " + path, e);
        }
    }

    public FileEntry upload(String path, byte[] content, String token) {
        String arg = writeArg(Map.of("path", path, "mode", "overwrite", "mute", true));
        Map<String, Object> resp = withRateLimitRetry(() -> restClient.post()
                .uri(CONTENT + "/2/files/upload")
                .header("Authorization", "Bearer " + token)
                .header("Dropbox-API-Arg", arg)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        if (resp == null || resp.get("id") == null) {
            throw new ClientException("Dropbox upload returned no id for path: " + path);
        }
        return toFileEntry(resp);
    }

    public byte[] downloadAsBytes(String path, String token) {
        String arg = writeArg(Map.of("path", path));
        byte[] bytes;
        try {
            bytes = withRateLimitRetry(() -> restClient.post()
                    .uri(CONTENT + "/2/files/download")
                    .header("Authorization", "Bearer " + token)
                    .header("Dropbox-API-Arg", arg)
                    .retrieve()
                    .body(byte[].class));
        } catch (RestClientResponseException e) {
            throw notFoundOrSame(e, path);
        }
        if (bytes == null) {
            throw new ClientException("Dropbox download returned no content for path: " + path);
        }
        return bytes;
    }

    public String downloadAsString(String path, String token) {
        return new String(downloadAsBytes(path, token), StandardCharsets.UTF_8);
    }

    public FileEntry getMetadata(String path, String token) {
        Map<String, Object> resp;
        try {
            resp = rpc(API + "/2/files/get_metadata", Map.of("path", path), token);
        } catch (RestClientResponseException e) {
            throw notFoundOrSame(e, path);
        }
        if (resp == null || resp.get("id") == null) {
            throw new ClientException("Dropbox get_metadata returned no id for path: " + path);
        }
        return toFileEntry(resp);
    }

    public ListResult listFolder(String path, int limit, String token) {
        return parseList(rpc(API + "/2/files/list_folder", Map.of("path", path, "limit", limit), token));
    }

    public ListResult listFolderContinue(String cursor, String token) {
        return parseList(rpc(API + "/2/files/list_folder/continue", Map.of("cursor", cursor), token));
    }

    private Map<String, Object> rpc(String uri, Map<String, Object> body, String token) {
        return withRateLimitRetry(() -> restClient.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    /**
     * Dropbox throttles with 429 + {@code Retry-After} (seconds). Wait and retry a couple of times
     * when the wait is short; a longer back-off is not worth holding the request thread for.
     * Every call made through here is idempotent (uploads use mode=overwrite).
     */
    private <T> T withRateLimitRetry(Supplier<T> call) {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                long waitSeconds = retryAfterSeconds(e);
                if (e.getStatusCode().value() != 429
                        || attempt >= MAX_RATE_LIMIT_RETRIES
                        || waitSeconds > MAX_RETRY_AFTER_SECONDS) {
                    throw e;
                }
                log.warn("Dropbox rate limit hit, retrying in {}s (attempt {})", waitSeconds, attempt + 1);
                try {
                    sleeper.sleep(waitSeconds * 1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static long retryAfterSeconds(RestClientResponseException e) {
        String header = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
        try {
            return header != null ? Math.max(0, Long.parseLong(header.trim())) : 1;
        } catch (NumberFormatException notSeconds) {
            return 1;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @SuppressWarnings("unchecked")
    private ListResult parseList(Map<String, Object> resp) {
        if (resp == null) {
            throw new ClientException("Dropbox list_folder returned null");
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) resp.get("entries");
        List<FileEntry> files = new ArrayList<>();
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                if ("file".equals(e.get(".tag"))) {
                    files.add(toFileEntry(e));
                }
            }
        }
        String cursor = (String) resp.get("cursor");
        boolean hasMore = Boolean.TRUE.equals(resp.get("has_more"));
        return new ListResult(files, cursor, hasMore);
    }

    private static FileEntry toFileEntry(Map<String, Object> e) {
        return new FileEntry(
                (String) e.get("id"),
                (String) e.get("name"),
                (String) e.get("path_display"),
                (String) e.get("server_modified"));
    }

    private String writeArg(Map<String, Object> arg) {
        try {
            return headerSafe(objectMapper.writeValueAsString(arg));
        } catch (Exception e) {
            throw new ClientException("Failed to build Dropbox-API-Arg header", e);
        }
    }

    /** Dropbox requires JSON sent in a header to be ASCII: 0x7F and above become JSON unicode escapes. */
    static String headerSafe(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c >= 0x7F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Dropbox reports endpoint-specific errors as 409 with the reason in the body's {@code error_summary}. */
    private static boolean isEndpointError(RestClientResponseException e, String reason) {
        return e.getStatusCode().value() == 409 && e.getResponseBodyAsString().contains(reason);
    }

    /** A missing file is a 409 {@code path/not_found}; callers map "not found" messages to HTTP 404. */
    private static RuntimeException notFoundOrSame(RestClientResponseException e, String path) {
        return isEndpointError(e, "not_found")
                ? new ClientException("Dropbox file not found: " + path, e)
                : e;
    }

    public record FileEntry(String id, String name, String pathDisplay, String serverModified) {
    }

    public record ListResult(List<FileEntry> files, String cursor, boolean hasMore) {
    }
}
