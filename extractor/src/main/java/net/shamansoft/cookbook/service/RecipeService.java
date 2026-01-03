package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Core business logic for recipe operations.
 * Orchestrates Drive access, YAML parsing, and DTO mapping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {
    private final DriveService googleDriveService;
    private final StorageService storageService;
    private final RecipeParser recipeParser;
    private final RecipeMapper recipeMapper;

    /**
     * List all recipes from user's Drive folder with full parsing.
     *
     * @param userId    Firebase user ID
     * @param pageSize  Number of items per page (1-100)
     * @param pageToken Pagination token from previous response (null for first page)
     * @return RecipeListResult with recipes and next page token
     * @throws net.shamansoft.cookbook.exception.StorageNotConnectedException if storage not connected
     * @throws net.shamansoft.cookbook.exception.DatabaseUnavailableException if Firestore unavailable
     */
    public RecipeListResult listRecipes(String userId, int pageSize, String pageToken) {
        log.info("Listing recipes for user: {}, pageSize: {}, pageToken: {}",
                userId, pageSize, pageToken);

        // 1. Get user's OAuth token
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.type() != StorageType.GOOGLE_DRIVE) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.type());
        }

        // 2. Get folder ID (custom or default "kukbuk" folder)
        String folderId;
        if (storage.defaultFolderId() != null) {
            log.debug("Using custom folder ID: {}", storage.defaultFolderId());
            folderId = storage.defaultFolderId();
        } else {
            log.debug("Using default folder (will create if needed)");
            folderId = googleDriveService.getOrCreateFolder(storage.accessToken());
        }

        // 3. List YAML files from Drive
        GoogleDrive.DriveFileListResult driveFiles = googleDriveService.listRecipeFiles(
                storage.accessToken(), folderId, pageSize, pageToken);

        log.info("Found {} YAML files in folder: {}", driveFiles.files().size(), folderId);

        // 4. Download and parse each YAML file
        List<RecipeDto> recipes = driveFiles.files().stream()
                .map(file -> parseRecipeFile(storage.accessToken(), file))
                .filter(Objects::nonNull)  // Skip parsing errors
                .toList();

        log.info("Successfully parsed {} out of {} recipes",
                recipes.size(), driveFiles.files().size());

        return new RecipeListResult(recipes, driveFiles.nextPageToken());
    }

    /**
     * Get single recipe by Drive file ID.
     *
     * @param userId Firebase user ID
     * @param fileId Google Drive file ID
     * @return Recipe DTO with full data
     * @throws net.shamansoft.cookbook.exception.StorageNotConnectedException if storage not connected
     * @throws RecipeNotFoundException                                        if file not found
     * @throws net.shamansoft.cookbook.exception.InvalidRecipeFormatException if YAML invalid
     */
    public RecipeDto getRecipe(String userId, String fileId) {
        log.info("Getting recipe: {} for user: {}", fileId, userId);

        // 1. Get user's OAuth token
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.type() != StorageType.GOOGLE_DRIVE) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.type());
        }

        try {
            // 2. Get file metadata
            GoogleDrive.DriveFileMetadata metadata = googleDriveService.getFileMetadata(
                    storage.accessToken(), fileId);

            log.debug("Found file: {} ({})", metadata.name(), metadata.mimeType());

            // 3. Download and parse YAML
            String yamlContent = googleDriveService.getFileContent(
                    storage.accessToken(), fileId);

            Recipe recipe = recipeParser.parse(yamlContent);

            // 4. Map to DTO
            RecipeDto dto = recipeMapper.toDto(recipe, metadata);

            log.info("Successfully retrieved recipe: {}", dto.getTitle());
            return dto;

        } catch (Exception e) {
            log.error("Failed to get recipe: {}", fileId, e);

            // Check if it's a "not found" error from Drive API
            if (e.getMessage() != null &&
                    (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
                throw new RecipeNotFoundException("Recipe not found: " + fileId, e);
            }

            throw e;
        }
    }

    /**
     * Parse a single recipe file from Drive.
     * Returns null if parsing fails (errors are logged but not thrown).
     *
     * @param authToken OAuth access token
     * @param fileInfo  Drive file info
     * @return Parsed RecipeDto or null if parsing failed
     */
    private RecipeDto parseRecipeFile(String authToken, GoogleDrive.DriveFileInfo fileInfo) {
        try {
            log.debug("Parsing recipe file: {} ({})", fileInfo.name(), fileInfo.id());

            String yamlContent = googleDriveService.getFileContent(authToken, fileInfo.id());
            Recipe recipe = recipeParser.parse(yamlContent);
            RecipeDto dto = recipeMapper.toDto(recipe, fileInfo);

            log.debug("Successfully parsed: {}", dto.getTitle());
            return dto;

        } catch (Exception e) {
            log.error("Failed to parse recipe file: {} - Skipping. Error: {}",
                    fileInfo.name(), e.getMessage());
            return null;  // Skip invalid recipes in list endpoint
        }
    }

    /**
     * Result of listing recipes with pagination.
     *
     * @param recipes       List of recipe DTOs
     * @param nextPageToken Token for next page (null if no more pages)
     */
    public record RecipeListResult(List<RecipeDto> recipes, String nextPageToken) {
    }
}
