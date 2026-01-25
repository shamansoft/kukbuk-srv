package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeServiceTest {

    @Test
    void createRecipe_storesYaml_whenRecipeDetected() throws Exception {
        // Arrange: minimal happy path with many dependencies mocked
        ContentHashService contentHashService = mock(ContentHashService.class);
        DriveService driveService = mock(DriveService.class);
        StorageService storageService = mock(StorageService.class);
        RecipeStoreService recipeStoreService = mock(RecipeStoreService.class);
        RecipeParser recipeParser = mock(RecipeParser.class);
        RecipeMapper recipeMapper = mock(RecipeMapper.class);
        HtmlExtractor htmlExtractor = mock(HtmlExtractor.class);
        HtmlCleaner htmlCleaner = mock(HtmlCleaner.class);
        Transformer transformer = mock(Transformer.class);
        RecipeValidationService validationService = mock(RecipeValidationService.class);

        when(contentHashService.generateContentHash(anyString())).thenReturn("hash1");

        // storage info - configured (use canonical record constructor)
        StorageInfo storage = new StorageInfo(
                net.shamansoft.cookbook.dto.StorageType.GOOGLE_DRIVE,
                true,
                "token",
                null,
                null,
                Instant.now(),
                "folder1",
                null
        );

        when(storageService.getStorageInfo(anyString())).thenReturn(storage);

        when(htmlExtractor.extractHtml(anyString(), any(), org.mockito.ArgumentMatchers.isNull())).thenReturn("<html><body>content</body></html>");

        HtmlCleaner.Results results = new HtmlCleaner.Results("<body>cleaned</body>", 100, 20, 0.8, net.shamansoft.cookbook.html.strategy.Strategy.CONTENT_FILTER, "msg");
        when(htmlCleaner.process(anyString(), anyString())).thenReturn(results);

        Recipe recipe = mock(Recipe.class);
        when(transformer.transform(anyString())).thenReturn(Transformer.Response.recipe(recipe));

        // validation service serializes object to YAML
        when(validationService.toYaml(recipe)).thenReturn("title: x\ningredients: []\n");

        when(driveService.generateFileName(anyString())).thenReturn("file.yaml");
        DriveService.UploadResult uploadResult = new DriveService.UploadResult("id1", "https://drive/file");
        when(driveService.uploadRecipeYaml(anyString(), anyString(), anyString(), anyString())).thenReturn(uploadResult);

        // Build service
        RecipeService svc = new RecipeService(contentHashService, driveService, storageService, recipeStoreService,
                recipeParser, recipeMapper, htmlExtractor, htmlCleaner, transformer, validationService);

        // Act
        var resp = svc.createRecipe("user1", "http://u", "rawHtml", null, "My Title");

        // Assert
        assertThat(resp.isRecipe()).isTrue();
        assertThat(resp.driveFileId()).isEqualTo("id1");
        assertThat(resp.driveFileUrl()).isEqualTo("https://drive/file");
    }

    @Test
    void createRecipe_throwsWhenNoFolder() throws IOException {
        ContentHashService contentHashService = mock(ContentHashService.class);
        DriveService driveService = mock(DriveService.class);
        StorageService storageService = mock(StorageService.class);
        RecipeStoreService recipeStoreService = mock(RecipeStoreService.class);
        RecipeParser recipeParser = mock(RecipeParser.class);
        RecipeMapper recipeMapper = mock(RecipeMapper.class);
        HtmlExtractor htmlExtractor = mock(HtmlExtractor.class);
        HtmlCleaner htmlCleaner = mock(HtmlCleaner.class);
        Transformer transformer = mock(Transformer.class);
        RecipeValidationService validationService = mock(RecipeValidationService.class);

        StorageInfo storage = new StorageInfo(net.shamansoft.cookbook.dto.StorageType.GOOGLE_DRIVE,
                true, "t", null, null, Instant.now(), null, null);

        when(storageService.getStorageInfo(anyString())).thenReturn(storage);

        RecipeService svc = new RecipeService(contentHashService, driveService, storageService, recipeStoreService,
                recipeParser, recipeMapper, htmlExtractor, htmlCleaner, transformer, validationService);

        try {
            svc.createRecipe("user1", "http://u", "rawHtml", null, "title");
            throw new AssertionError("Expected StorageNotConnectedException");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(net.shamansoft.cookbook.exception.StorageNotConnectedException.class);
        }
    }
}
