package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.RecipeItemResult;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core business logic for recipe operations.
 * Orchestrates Drive access, YAML parsing, and DTO mapping.
 * Supports extracting multiple recipes from a single page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private final ContentHashService contentHashService;
    private final DriveService googleDriveService;
    private final StorageService storageService;
    private final RecipeStoreService recipeStoreService;
    private final RecipeParser recipeParser;
    private final RecipeMapper recipeMapper;
    private final HtmlExtractor htmlExtractor;
    private final Transformer transformer;  // AdaptiveCleaningTransformerService (@Primary)
    private final RecipeValidationService validationService;

    public RecipeResponse createRecipe(String userId, String url, String sourceHtml, String compression, String title) throws IOException {
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.folderId() == null) {
            throw new StorageNotConnectedException(
                    "No folder configured for recipe storage. Please reconnect Google Drive or configure a folder.");
        }

        var transformerResponse = createOrGetCached(url, sourceHtml, compression);
        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(title)
                .url(url)
                .isRecipe(transformerResponse.isRecipe());

        if (transformerResponse.isRecipe()) {
            List<RecipeItemResult> uploadedRecipes = new ArrayList<>();

            for (Recipe recipe : transformerResponse.recipes()) {
                String recipeTitle = recipe.metadata() != null && recipe.metadata().title() != null
                        ? recipe.metadata().title() : title;
                String fileName = googleDriveService.generateFileName(recipeTitle);
                String yamlContent = convertRecipeToYaml(recipe);
                DriveService.UploadResult uploadResult = googleDriveService.uploadRecipeYaml(
                        storage.accessToken(), storage.folderId(), fileName, yamlContent);
                uploadedRecipes.add(new RecipeItemResult(recipeTitle, uploadResult.fileId(), uploadResult.fileUrl()));
            }

            responseBuilder.recipes(uploadedRecipes);

            // Backward compat: populate top-level fields from the first recipe
            if (!uploadedRecipes.isEmpty()) {
                RecipeItemResult first = uploadedRecipes.get(0);
                responseBuilder
                        .title(first.title())
                        .driveFileId(first.driveFileId())
                        .driveFileUrl(first.driveFileUrl());
            }
        } else {
            log.info("Content is not a recipe. Skipping Drive storage - URL: {}", url);
        }

        return responseBuilder.build();
    }

    private Transformer.Response createOrGetCached(String url, String sourceHtml, String compression) throws IOException {
        String contentHash = contentHashService.generateContentHash(url);
        Optional<RecipeStoreService.CachedRecipes> cached = recipeStoreService.findCachedRecipes(contentHash);
        if (cached.isPresent()) {
            RecipeStoreService.CachedRecipes hit = cached.get();
            if (hit.valid()) {
                List<Recipe> recipes = hit.recipes();
                log.debug("Cache HIT: {} recipe(s) for hash: {}", recipes.size(), contentHash);
                return recipes.size() == 1
                        ? Transformer.Response.recipe(recipes.get(0))
                        : Transformer.Response.recipes(recipes);
            } else {
                return Transformer.Response.notRecipe();
            }
        }

        // Cache miss — extract and transform
        String html = htmlExtractor.extractHtml(url, sourceHtml, compression);
        log.info("Extracted HTML - URL: {}, HTML length: {} chars, Content hash: {}", url, html.length(), contentHash);

        var response = transformer.transform(html, url);

        if (response.isRecipe()) {
            recipeStoreService.storeValidRecipes(contentHash, url, response.recipes());
            log.debug("Cached {} recipe(s) for hash: {}", response.recipes().size(), contentHash);
        } else {
            log.warn("Gemini determined content is NOT a recipe - URL: {}, Hash: {}", url, contentHash);
            recipeStoreService.storeInvalidRecipe(contentHash, url);
        }
        return response;
    }

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

        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.type() != StorageType.GOOGLE_DRIVE) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.type());
        }

        if (storage.folderId() == null) {
            throw new StorageNotConnectedException(
                    "No folder configured for recipe storage. Please reconnect Google Drive or configure a folder.");
        }

        String folderId = storage.folderId();
        log.debug("Using folder ID from user profile: {}", folderId);

        GoogleDrive.DriveFileListResult driveFiles = googleDriveService.listRecipeFiles(
                storage.accessToken(), folderId, pageSize, pageToken);

        log.info("Found {} YAML files in folder: {}", driveFiles.files().size(), folderId);

        List<RecipeDto> recipes = driveFiles.files().stream()
                .map(file -> parseRecipeFile(storage.accessToken(), file))
                .filter(Objects::nonNull)
                .toList();

        log.info("Successfully parsed {} out of {} recipes", recipes.size(), driveFiles.files().size());

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

        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.type() != StorageType.GOOGLE_DRIVE) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.type());
        }

        try {
            GoogleDrive.DriveFileMetadata metadata = googleDriveService.getFileMetadata(
                    storage.accessToken(), fileId);

            log.debug("Found file: {} ({})", metadata.name(), metadata.mimeType());

            String yamlContent = googleDriveService.getFileContent(storage.accessToken(), fileId);
            Recipe recipe = recipeParser.parse(yamlContent);
            RecipeDto dto = recipeMapper.toDto(recipe, metadata);

            log.info("Successfully retrieved recipe: {}", dto.getTitle());
            return dto;

        } catch (Exception e) {
            log.error("Failed to get recipe: {}", fileId, e);

            if (e.getMessage() != null &&
                    (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
                throw new RecipeNotFoundException("Recipe not found: " + fileId, e);
            }

            throw e;
        }
    }

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
            return null;
        }
    }

    private String convertRecipeToYaml(Recipe recipe) {
        try {
            return validationService.toYaml(recipe);
        } catch (RecipeSerializeException e) {
            log.error("Failed to serialize Recipe to YAML - Title: {}",
                    recipe.metadata() != null ? recipe.metadata().title() : "N/A", e);
            throw new RuntimeException("Failed to convert recipe to YAML for storage", e);
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
