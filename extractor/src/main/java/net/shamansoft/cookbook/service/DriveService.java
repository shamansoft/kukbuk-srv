package net.shamansoft.cookbook.service;

public interface DriveService {

    record UploadResult(String fileId, String fileUrl) {
    }

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

    String getOrCreateFolder(String authToken);

    UploadResult uploadRecipeYaml(String authToken, String folderId, String fileName, String content);
}
