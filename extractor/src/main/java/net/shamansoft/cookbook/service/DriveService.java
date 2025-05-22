package net.shamansoft.cookbook.service;

public interface DriveService {

    /**
     * Generates a clean filename for the recipe YAML based on the title.
     *
     * @param title the recipe title
     * @return sanitized filename ending with .yaml
     */
    default String generateFileName(String title) {
        String base = (title == null || title.isBlank()) ? "recipe-" + System.currentTimeMillis() : title;
        String clean = base.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (clean.endsWith("-")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean + ".yaml";
    }

    /**
     * Retrieves or creates the configured folder in the user's Google Drive.
     * <p>This is a stub; real implementation should use Drive HTTP API or client library.</p>
     *
     * @param authToken OAuth2 access token
     * @return ID of the Drive folder
     */
    String getOrCreateFolder(String authToken);

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
    UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content);

    record UploadResult(String fileId, String fileUrl) {
    }
}
