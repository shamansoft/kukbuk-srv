package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.client.DropboxClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeWriteUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * {@link StorageProvider} backed by Dropbox (App-folder access). Paths are relative to the
 * app folder; {@code ""} is the app-folder root. Recipe/media files are addressed by Dropbox id.
 */
@Slf4j
@Service
public class DropboxStorageProvider implements StorageProvider {

    private static final int WRITE_ATTEMPTS = 3;
    private static final String DROPBOX_HOME = "https://www.dropbox.com/home";

    private final DropboxClient dropboxClient;
    private final Transliterator transliterator;

    /** Name of the app folder under the user's /Apps (= the Dropbox app's name); only used to build web links. */
    @Value("${cookbook.dropbox.app-folder-name:}")
    private String appFolderName;

    public DropboxStorageProvider(DropboxClient dropboxClient, Transliterator transliterator) {
        this.dropboxClient = dropboxClient;
        this.transliterator = transliterator;
    }

    @Override
    public StorageType type() {
        return StorageType.DROPBOX;
    }

    @Override
    public String generateFileName(String title) {
        String lowerAscii = transliterator.toAsciiKebab(title);
        String base = lowerAscii == null || lowerAscii.isEmpty()
                ? "recipe-" + System.currentTimeMillis()
                : lowerAscii;
        return base + ".yaml";
    }

    @Override
    public FolderRef getOrCreateFolder(String accessToken, String folderName) {
        String sub = normalizeSubfolder(folderName);
        if (sub.isEmpty()) {
            // App-folder root: nothing to create (Dropbox provisions the app folder on first write).
            return new FolderRef("", folderName == null ? "" : folderName.trim());
        }
        dropboxClient.createFolder(sub, accessToken);
        return new FolderRef(sub, folderName.trim());
    }

    @Override
    public DriveService.UploadResult saveRecipeYaml(String accessToken, String folderId, String fileName,
                                                    Function<Optional<String>, String> edit) {
        String path = joinPath(folderId, fileName);
        DropboxClient.FileEntry entry = retryWrite(() -> {
            DropboxClient.RecipeDownload current;
            try {
                current = dropboxClient.downloadForWrite(path, accessToken);
            } catch (DropboxClient.FileMissingException missing) {
                String yaml = edit.apply(Optional.empty());
                return dropboxClient.create(path, yaml.getBytes(StandardCharsets.UTF_8), accessToken);
            }
            String yaml = edit.apply(Optional.of(current.yaml()));
            return dropboxClient.update(current.id(), current.revision(), yaml.getBytes(StandardCharsets.UTF_8), accessToken);
        });
        return new DriveService.UploadResult(entry.id(), webUrl(entry.pathDisplay() != null ? entry.pathDisplay() : path));
    }

    /**
     * Browser link to a file. Clients open {@code fileUrl} as-is, so it must be a real URL, not a
     * Dropbox path. Without a configured app-folder name the best we can do is the Dropbox home.
     */
    private String webUrl(String pathInAppFolder) {
        if (appFolderName == null || appFolderName.isBlank()) {
            return DROPBOX_HOME;
        }
        return DROPBOX_HOME + UriUtils.encodePath("/Apps/" + appFolderName.trim() + pathInAppFolder, StandardCharsets.UTF_8);
    }

    @Override
    public GoogleDrive.DriveFileListResult listRecipeFiles(String accessToken, String folderId, int pageSize, String pageToken) {
        DropboxClient.ListResult result = (pageToken == null)
                ? dropboxClient.listFolder(rootPath(folderId), pageSize, accessToken)
                : dropboxClient.listFolderContinue(pageToken, accessToken);

        List<GoogleDrive.DriveFileInfo> files = result.files().stream()
                .filter(f -> f.name() != null && f.name().endsWith(".yaml"))
                .map(f -> new GoogleDrive.DriveFileInfo(f.id(), f.name(), f.serverModified()))
                .toList();

        String nextPageToken = result.hasMore() ? result.cursor() : null;
        return new GoogleDrive.DriveFileListResult(files, nextPageToken);
    }

    @Override
    public String getFileContent(String accessToken, String fileId) {
        return dropboxClient.downloadAsString(fileRef(fileId), accessToken);
    }

    @Override
    public byte[] downloadFile(String accessToken, String fileId) {
        return dropboxClient.downloadAsBytes(fileRef(fileId), accessToken);
    }

    @Override
    public String getFileMimeType(String accessToken, String fileId) {
        return mimeTypeForName(dropboxClient.getMetadata(fileRef(fileId), accessToken).name());
    }

    @Override
    public GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        DropboxClient.FileEntry e = dropboxClient.getMetadata(fileRef(fileId), accessToken);
        return new GoogleDrive.DriveFileMetadata(e.id(), e.name(), mimeTypeForName(e.name()), e.serverModified());
    }

    @Override
    public String editRecipeYaml(String accessToken, String fileId, UnaryOperator<String> edit) {
        return retryWrite(() -> {
            DropboxClient.RecipeDownload current = dropboxClient.downloadForWrite(fileRef(fileId), accessToken);
            String yaml = edit.apply(current.yaml());
            dropboxClient.update(current.id(), current.revision(), yaml.getBytes(StandardCharsets.UTF_8), accessToken);
            return yaml;
        });
    }

    private static <T> T retryWrite(Supplier<T> write) {
        for (int attempt = 1; ; attempt++) {
            try {
                return write.get();
            } catch (DropboxClient.WriteConflictException conflict) {
                if (attempt == WRITE_ATTEMPTS) {
                    throw new RecipeWriteUnavailableException("Dropbox recipe changed during each upload attempt", conflict);
                }
            }
        }
    }

    /**
     * Dropbox only accepts {@code id:...} or {@code /path} references. Anything else (e.g. a Google
     * Drive id a client cached before the user switched provider) cannot exist here: report it as
     * not found instead of letting Dropbox reject the request as malformed.
     */
    static String fileRef(String fileId) {
        if (fileId == null || !(fileId.startsWith("id:") || fileId.startsWith("/"))) {
            throw new ClientException("Dropbox file not found: " + fileId);
        }
        return fileId;
    }

    /** App-folder root is "" (also accept "/"); a stored subfolder path is returned as-is. */
    static String rootPath(String folderId) {
        return (folderId == null || folderId.isBlank() || "/".equals(folderId)) ? "" : folderId;
    }

    static String joinPath(String folderId, String fileName) {
        String root = rootPath(folderId);
        return root.isEmpty() ? "/" + fileName : root + "/" + fileName;
    }

    static String normalizeSubfolder(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return "";
        }
        String n = folderName.trim();
        return n.startsWith("/") ? n : "/" + n;
    }

    static String mimeTypeForName(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/x-yaml";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }
}
