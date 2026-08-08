package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.DropboxClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * {@link StorageProvider} backed by Dropbox (App-folder access). Paths are relative to the
 * app folder; {@code ""} is the app-folder root. Recipe/media files are addressed by Dropbox id.
 */
@Slf4j
@Service
public class DropboxStorageProvider implements StorageProvider {

    private final DropboxClient dropboxClient;
    private final Transliterator transliterator;

    @Value("${cookbook.dropbox.folder-name:}")
    private String folderName;

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
    public DriveService.UploadResult uploadRecipeYaml(String accessToken, String folderId, String fileName, String content) {
        String path = joinPath(folderId, fileName);
        DropboxClient.FileEntry entry =
                dropboxClient.upload(path, content.getBytes(StandardCharsets.UTF_8), accessToken);
        String url = entry.pathDisplay() != null ? entry.pathDisplay() : path;
        return new DriveService.UploadResult(entry.id(), url);
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
        return dropboxClient.downloadAsString(fileId, accessToken);
    }

    @Override
    public byte[] downloadFile(String accessToken, String fileId) {
        return dropboxClient.downloadAsBytes(fileId, accessToken);
    }

    @Override
    public String getFileMimeType(String accessToken, String fileId) {
        return mimeTypeForName(dropboxClient.getMetadata(fileId, accessToken).name());
    }

    @Override
    public GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        DropboxClient.FileEntry e = dropboxClient.getMetadata(fileId, accessToken);
        return new GoogleDrive.DriveFileMetadata(e.id(), e.name(), mimeTypeForName(e.name()), e.serverModified());
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
