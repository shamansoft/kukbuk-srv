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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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
    private String writtenYaml;
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

    private void stubEdit(String yaml) {
        when(provider.editRecipeYaml(eq(TOKEN), eq(FILE_ID), any())).thenAnswer(invocation -> {
            writtenYaml = invocation.<UnaryOperator<String>>getArgument(2).apply(yaml);
            return writtenYaml;
        });
    }

    @Test
    void setsNormalizedTagsAndKeepsOtherFields() {
        stubEdit(STORED_YAML);

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of(" Weeknight ", "KIDS", "weeknight", "", "  ")));

        assertThat(dto.getUserTags()).containsExactly("weeknight", "kids");
        assertThat(dto.getTags()).containsExactly("Breakfast");
        assertThat(dto.getId()).isEqualTo(FILE_ID);
        assertThat(dto.getLastModified()).isEqualTo(MODIFIED);

        String yaml = writtenYaml;
        assertThat(yaml).contains("user_tags:").contains("custom_key: \"kept as is\"");
        Recipe stored = parser.parse(yaml);
        assertThat(stored.userTags()).containsExactly("weeknight", "kids");
        assertThat(stored.metadata().title()).isEqualTo("Pancakes");
        assertThat(stored.metadata().tags()).containsExactly("Breakfast");
        assertThat(stored.ingredients()).hasSize(1);
    }

    @Test
    void replacesExistingTags() {
        stubEdit(STORED_YAML + "user_tags:\n- \"old\"\n- \"older\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(List.of("new")));

        assertThat(dto.getUserTags()).containsExactly("new");
        assertThat(parser.parse(writtenYaml).userTags()).containsExactly("new");
    }

    @Test
    void emptyListClearsTagsAndRemovesKey() {
        stubEdit(STORED_YAML + "user_tags:\n- \"old\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(List.of()));

        assertThat(dto.getUserTags()).isEmpty();
        assertThat(writtenYaml).doesNotContain("user_tags").contains("custom_key");
    }

    @Test
    void nullTagsClearTags() {
        stubEdit(STORED_YAML + "user_tags:\n- \"old\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(null));

        assertThat(dto.getUserTags()).isEmpty();
        assertThat(writtenYaml).doesNotContain("user_tags");
    }

    @Test
    void setsUserNotesAndKeepsTags() {
        stubEdit(STORED_YAML + "user_tags:\n- \"kept\"\n");

        RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserNotes("Less sugar.\n\nBake 5 min more.  \n"));

        assertThat(dto.getUserNotes()).isEqualTo("Less sugar.\n\nBake 5 min more.");
        assertThat(dto.getUserTags()).containsExactly("kept");
        assertThat(dto.getNotes()).isEqualTo("");
        Recipe stored = parser.parse(writtenYaml);
        assertThat(stored.userNotes()).isEqualTo("Less sugar.\n\nBake 5 min more.");
        assertThat(stored.userTags()).containsExactly("kept");
        assertThat(stored.notes()).isEmpty();
    }

    @Test
    void tagsOnlyUpdateLeavesUserNotesUnchanged() {
        stubEdit(STORED_YAML + "user_notes: \"mine\"\n");

        service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserTags(List.of("a")));

        assertThat(parser.parse(writtenYaml).userNotes()).isEqualTo("mine");
    }

    @Test
    void blankOrNullNotesClearNoteAndRemoveKey() {
        for (String cleared : new String[]{"", "  \n ", null}) {
            org.mockito.Mockito.clearInvocations(provider);
            stubEdit(STORED_YAML + "user_notes: \"old\"\n");

            RecipeDto dto = service.updateRecipe(USER_ID, FILE_ID, RecipeUpdateRequest.withUserNotes(cleared));

            assertThat(dto.getUserNotes()).isNull();
            assertThat(writtenYaml).doesNotContain("user_notes").contains("custom_key");
        }
    }

    @Test
    void tooLongNoteIsRejectedBeforeAnyIo() {
        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserNotes("x".repeat(5001))))
                .isInstanceOf(InvalidRecipeUpdateException.class);
        verify(provider, never()).editRecipeYaml(anyString(), anyString(), any());
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
        when(provider.editRecipeYaml(eq(TOKEN), eq(FILE_ID), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of("a"))))
                .isInstanceOf(RecipeNotFoundException.class);
        assertThat(writtenYaml).isNull();
    }

    @Test
    void invalidStoredFileIsNotOverwritten() {
        stubEdit("- just\n- a list\n");

        assertThatThrownBy(() -> service.updateRecipe(USER_ID, FILE_ID,
                RecipeUpdateRequest.withUserTags(List.of("a"))))
                .isInstanceOf(InvalidRecipeFormatException.class);
        assertThat(writtenYaml).isNull();
    }

    // ---- re-extraction keeps user data ----------------------------------------

    private static Recipe extracted() {
        return new Recipe(true, "1.0.0", "1.0.0",
                new RecipeMetadata("Pancakes", "https://example.com/pancakes", null, null, null, null,
                        List.of("Breakfast"), null, null, null, null, null, null),
                null, List.of(), null, List.of(), null, null, null);
    }

    private void stubCapture(Recipe recipe) {
        when(contentHashService.generateContentHash(anyString())).thenReturn("hash");
        when(recipeStoreService.findCachedRecipes("hash"))
                .thenReturn(Optional.of(RecipeStoreService.CachedRecipes.valid(List.of(recipe))));
        when(provider.generateFileName("Pancakes")).thenReturn("pancakes.yaml");
    }

    private void stubResave(Optional<String> currentYaml) {
        when(provider.saveRecipeYaml(eq(TOKEN), eq("folder"), eq("pancakes.yaml"), any()))
                .thenAnswer(invocation -> {
                    Function<Optional<String>, String> edit = invocation.getArgument(3);
                    writtenYaml = edit.apply(currentYaml);
                    return new DriveService.UploadResult(FILE_ID, "https://drive/file");
                });
    }

    @Test
    void createRecipe_overwritingFileKeepsItsUserData() throws Exception {
        stubCapture(extracted());
        stubResave(Optional.of(STORED_YAML + "user_notes: Less sugar\nuser_tags: [weeknight]\n"));

        service.createRecipe(USER_ID, "https://example.com/pancakes", null, null, null);

        Recipe stored = parser.parse(writtenYaml);
        assertThat(stored.userNotes()).isEqualTo("Less sugar");
        assertThat(stored.userTags()).containsExactly("weeknight");
        assertThat(stored.metadata().tags()).containsExactly("Breakfast");
    }

    @Test
    void createRecipe_withoutPriorFileDoesNotImportUserDataFromExtraction() throws Exception {
        stubCapture(extracted().withUserTags(List.of("model tag")).withUserNotes("model note"));
        stubResave(Optional.empty());

        service.createRecipe(USER_ID, "https://example.com/pancakes", null, null, null);

        Recipe stored = parser.parse(writtenYaml);
        assertThat(stored.userTags()).isEmpty();
        assertThat(stored.userNotes()).isEmpty();
    }

    @Test
    void createRecipe_abortsWhenStorageCannotReadExistingFile() {
        stubCapture(extracted());
        var readFailure = new IllegalStateException("drive 503");
        when(provider.saveRecipeYaml(eq(TOKEN), eq("folder"), eq("pancakes.yaml"), any())).thenThrow(readFailure);

        assertThatThrownBy(() -> service.createRecipe(USER_ID, "https://example.com/pancakes", null, null, null))
                .isSameAs(readFailure);
        assertThat(writtenYaml).isNull();
    }

    @Test
    void createRecipe_abortsWhenExistingFileContainsMalformedYaml() {
        stubCapture(extracted());
        stubResave(Optional.of(STORED_YAML + "user_tags: [unclosed\n"));

        assertThatThrownBy(() -> service.createRecipe(USER_ID, "https://example.com/pancakes", null, null, null))
                .isInstanceOf(InvalidRecipeFormatException.class);
        assertThat(writtenYaml).isNull();
    }
}
