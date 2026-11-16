package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.RecipeUpdateRequest;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.InvalidRecipeFormatException;
import net.shamansoft.cookbook.exception.InvalidRecipeUpdateException;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RecipeService#updateRecipe} (PATCH /v1/recipes/{id}) and for keeping user data when an
 * extraction overwrites an existing file. Uses the real YAML parser, serializer and mapper.
 */
class RecipeServiceUpdateTest {

    private static final String USER_ID = "user-1";
    private static final String TOKEN = "token";
    private static final String FILE_ID = "file-1";
    private static final String MODIFIED = "2026-11-15T10:00:00Z";

    private static final String STORED_YAML = """
            is_recipe: true
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Pancakes"
              source: "https://example.com/pancakes"
              tags:
              - "Breakfast"
            ingredients:
            - item: "flour"
            instructions:
            - description: "Mix and fry"
            custom_key: "kept as is"
            """;

    private StorageProvider provider;
    private StorageService storageService;
    private ContentHashService contentHashService;
    private RecipeStoreService recipeStoreService;
    private RecipeService service;
    private final RecipeParser parser = new RecipeParser();

    @BeforeEach
    void setUp() {
        provider = mock(StorageProvider.class);
        storageService = mock(StorageService.class);
        StorageProviderResolver resolver = mock(StorageProviderResolver.class);
        when(resolver.resolve(any(StorageType.class))).thenReturn(provider);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken(TOKEN)
                .connectedAt(Instant.now())
                .folderId("folder")
                .build());
        when(provider.getFileMetadata(TOKEN, FILE_ID))
                .thenReturn(new GoogleDrive.DriveFileMetadata(FILE_ID, "pancakes.yaml", "application/x-yaml", MODIFIED));

        contentHashService = mock(ContentHashService.class);
        recipeStoreService = mock(RecipeStoreService.class);
        service = new RecipeService(contentHashService, resolver, storageService,
                recipeStoreService, parser, new RecipeMapper(), mock(HtmlExtractor.class),
                mock(Compressor.class), mock(Transformer.class), new RecipeValidationService(),
                mock(GeminiRestTransformer.class),
                new RecipePostProcessor("1.0.0", Clock.systemUTC(), new RecipeAdjustmentService()));
    }

    private String writtenYaml() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(provider).updateFileContent(eq(TOKEN), eq(FILE_ID), content.capture());
        return content.getValue();
    }

    @Test
    void setsNormalizedTagsAndKeepsOtherFields() {
        when(provider.getFileContent(TOKEN, FILE_ID)).thenReturn(STORED_YAML);

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of(" Weeknight ", "KIDS", "weeknight", "", "  ")));

        assertThat(dto.getUserTags()).containsExactly("weeknight", "kids");
        assertThat(dto.getTags()).containsExactly("Breakfast");
        assertThat(dto.getId()).isEqualTo(FILE_ID);
        assertThat(dto.getLastModified()).isEqualTo(MODIFIED);

        String yaml = writtenYaml();
        assertThat(yaml).contains("user_tags:").contains("custom_key: \"kept as is\"");
        Recipe stored = parser.parse(yaml);
        assertThat(stored.userTags()).containsExactly("weeknight", "kids");
        assertThat(stored.metadata().title()).isEqualTo("Pancakes");
        assertThat(stored.metadata().tags()).containsExactly("Breakfast");
        assertThat(stored.ingredients()).hasSize(1);
    }

    @Test
    void replacesExistingTags() {
        when(provider.getFileContent(TOKEN, FILE_ID))
                .thenReturn(STORED_YAML + "user_tags:\n- \"old\"\n- \"older\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(List.of("new")));

        assertThat(dto.getUserTags()).containsExactly("new");
        assertThat(parser.parse(writtenYaml()).userTags()).containsExactly("new");
    }

    @Test
    void emptyListClearsTagsAndRemovesKey() {
        when(provider.getFileContent(TOKEN, FILE_ID)).thenReturn(STORED_YAML + "user_tags:\n- \"old\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(List.of()));

        assertThat(dto.getUserTags()).isEmpty();
        assertThat(writtenYaml()).doesNotContain("user_tags").contains("custom_key");
    }

    @Test
    void nullTagsClearTags() {
        when(provider.getFileContent(TOKEN, FILE_ID)).thenReturn(STORED_YAML + "user_tags:\n- \"old\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(null));

        assertThat(dto.getUserTags()).isEmpty();
        assertThat(writtenYaml()).doesNotContain("user_tags");
    }

    @Test
    void emptyRequestIsRejectedBeforeAnyIo() {
        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID, new RecipeUpdateRequest()))
                .isInstanceOf(InvalidRecipeUpdateException.class);
        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID, null))
                .isInstanceOf(InvalidRecipeUpdateException.class);
        verifyNoInteractions(storageService, provider);
    }

    @Test
    void tooManyTagsIsRejectedBeforeAnyIo() {
        String[] tags = new String[UserTags.MAX_TAGS + 1];
        Arrays.setAll(tags, i -> "tag" + i);

        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of(tags))))
                .isInstanceOf(InvalidRecipeUpdateException.class);
        verifyNoInteractions(storageService, provider);
    }

    @Test
    void missingFileIsNotFound() {
        when(provider.getFileContent(TOKEN, FILE_ID))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of("a"))))
                .isInstanceOf(RecipeNotFoundException.class);
        verify(provider, never()).updateFileContent(anyString(), anyString(), anyString());
    }

    @Test
    void invalidStoredFileIsNotOverwritten() {
        when(provider.getFileContent(TOKEN, FILE_ID)).thenReturn("- just\n- a list\n");

        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of("a"))))
                .isInstanceOf(InvalidRecipeFormatException.class);
        verify(provider, never()).updateFileContent(anyString(), anyString(), anyString());
    }

    // ---- re-extraction keeps user data ----------------------------------------

    private static Recipe extracted() {
        return new Recipe(true, "1.0.0", "1.0.0",
                new RecipeMetadata("Pancakes", "https://example.com/pancakes", null, null, null, null,
                        List.of("Breakfast"), null, null, null, null, null, null),
                null, List.of(), null, List.of(), null, null, null);
    }

    @Test
    void withExistingUserData_copiesTagsFromOverwrittenFile() {
        Recipe result = service.withExistingUserData(extracted(),
                Optional.of(STORED_YAML + "user_tags:\n- \"weeknight\"\n"));

        assertThat(result.userTags()).containsExactly("weeknight");
        assertThat(result.metadata().tags()).containsExactly("Breakfast");
    }

    @Test
    void withExistingUserData_noFileMeansNoTags() {
        Recipe fromLlm = extracted().withUserTags(List.of("should-not-be-stored"));

        assertThat(service.withExistingUserData(fromLlm, Optional.empty()).userTags()).isEmpty();
    }

    @Test
    void withExistingUserData_unreadableFileMeansNoTags() {
        assertThat(service.withExistingUserData(extracted(), Optional.of("not: [valid")).userTags()).isEmpty();
    }

    @Test
    void createRecipe_overwritingFileKeepsItsUserTags() throws Exception {
        when(contentHashService.generateContentHash(anyString())).thenReturn("hash");
        when(recipeStoreService.findCachedRecipes("hash"))
                .thenReturn(Optional.of(RecipeStoreService.CachedRecipes.valid(List.of(extracted()))));
        when(provider.generateFileName("Pancakes")).thenReturn("pancakes.yaml");
        when(provider.findRecipeYaml(TOKEN, "folder", "pancakes.yaml"))
                .thenReturn(Optional.of(STORED_YAML + "user_tags:\n- \"weeknight\"\n"));
        when(provider.uploadRecipeYaml(eq(TOKEN), eq("folder"), eq("pancakes.yaml"), anyString()))
                .thenReturn(new DriveService.UploadResult(FILE_ID, "https://drive/file"));

        service.createRecipe(USER_ID, "https://example.com/pancakes", null, null, null);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(provider).uploadRecipeYaml(eq(TOKEN), eq("folder"), eq("pancakes.yaml"), content.capture());
        assertThat(parser.parse(content.getValue()).userTags()).containsExactly("weeknight");
    }
}
