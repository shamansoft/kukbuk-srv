package net.shamansoft.cookbook.service.youtube;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.exception.TranscriptException;
import net.shamansoft.cookbook.exception.YouTubeApiException;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.gemini.GeminiClient;
import net.shamansoft.cookbook.service.gemini.GeminiRequest;
import net.shamansoft.cookbook.service.gemini.GeminiResponse;
import net.shamansoft.cookbook.service.gemini.RequestBuilder;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeTransformerTest {

    @Mock
    private YouTubeDataService youTubeDataService;

    @Mock
    private TranscriptServiceRouter transcriptServiceRouter;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private RequestBuilder requestBuilder;

    private YouTubeTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new YouTubeTransformer(
                youTubeDataService,
                transcriptServiceRouter,
                geminiClient,
                requestBuilder
        );
    }

    private Recipe createTestRecipe(String title, boolean isRecipe) {
        RecipeMetadata metadata = new RecipeMetadata(
                title,
                "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "Test Channel",
                "en",
                null, null, null,
                4,
                null, null, null, null, null
        );

        List<Ingredient> ingredients = List.of(
                new Ingredient("Sugar", "1", "cup", null, null, null, null)
        );

        List<Instruction> instructions = List.of(
                new Instruction(1, "Mix ingredients", null, null, null)
        );

        return new Recipe(
                isRecipe, "1.0.0", "1.0.0",
                metadata, null, ingredients, null, instructions,
                null, null, null
        );
    }

    @Nested
    class ExtractVideoId {

        @Test
        void extractsFromStandardUrl() {
            assertThat(YouTubeTransformer.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromStandardUrlWithAdditionalParams() {
            assertThat(YouTubeTransformer.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromShortUrl() {
            assertThat(YouTubeTransformer.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromEmbedUrl() {
            assertThat(YouTubeTransformer.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromShortsUrl() {
            assertThat(YouTubeTransformer.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromMobileUrl() {
            assertThat(YouTubeTransformer.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromUrlWithoutHttps() {
            assertThat(YouTubeTransformer.extractVideoId("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromUrlWithoutWww() {
            assertThat(YouTubeTransformer.extractVideoId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        void extractsFromUrlWithParamBeforeV() {
            assertThat(YouTubeTransformer.extractVideoId("https://www.youtube.com/watch?list=PLtest&v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://www.google.com",
                "not-a-url",
                "https://youtube.com/channel/UCtest",
                "https://youtube.com/watch?list=PLtest",
                ""
        })
        void throwsForInvalidUrls(String invalidUrl) {
            assertThatThrownBy(() -> YouTubeTransformer.extractVideoId(invalidUrl))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid YouTube URL");
        }

        @Test
        void throwsForNullUrl() {
            assertThatThrownBy(() -> YouTubeTransformer.extractVideoId(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void handlesVideoIdWithHyphensAndUnderscores() {
            assertThat(YouTubeTransformer.extractVideoId("https://youtu.be/a-B_c1D2e3F"))
                    .isEqualTo("a-B_c1D2e3F");
        }
    }

    @Nested
    class Transform {

        @Test
        void successfullyReturnsRecipe() {
            String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
            YouTubeMetadata metadata = new YouTubeMetadata(
                    "Test Recipe Video", "Ingredients: sugar, flour", "Test Channel", Instant.now());
            Recipe expectedRecipe = createTestRecipe("Test Recipe Video", true);
            GeminiRequest mockRequest = GeminiRequest.builder().build();

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ")).thenReturn(metadata);
            when(transcriptServiceRouter.getTranscript("dQw4w9WgXcQ")).thenReturn("Mix the sugar and flour");
            when(requestBuilder.buildYouTubeRecipeRequest("Test Recipe Video", "Ingredients: sugar, flour", "Mix the sugar and flour"))
                    .thenReturn(mockRequest);
            when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                    .thenReturn(GeminiResponse.success(expectedRecipe, "{\"is_recipe\": true}"));

            Transformer.Response response = transformer.transform(null, videoUrl);

            assertThat(response).isNotNull();
            assertThat(response.isRecipe()).isTrue();
            assertThat(response.recipe()).isEqualTo(expectedRecipe);
            assertThat(response.rawLlmResponse()).isEqualTo("{\"is_recipe\": true}");

            verify(youTubeDataService).getMetadata("dQw4w9WgXcQ");
            verify(transcriptServiceRouter).getTranscript("dQw4w9WgXcQ");
            verify(requestBuilder).buildYouTubeRecipeRequest("Test Recipe Video", "Ingredients: sugar, flour", "Mix the sugar and flour");
            verify(geminiClient).request(eq(mockRequest), eq(Recipe.class));
        }

        @Test
        void returnsNotRecipeWhenContentIsNotRecipe() {
            String videoUrl = "https://youtu.be/dQw4w9WgXcQ";
            YouTubeMetadata metadata = new YouTubeMetadata(
                    "My Cat Video", "Watch my cat do stuff", "Cat Channel", Instant.now());
            Recipe nonRecipe = createTestRecipe("My Cat Video", false);
            GeminiRequest mockRequest = GeminiRequest.builder().build();

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ")).thenReturn(metadata);
            when(transcriptServiceRouter.getTranscript("dQw4w9WgXcQ")).thenReturn("Look at my cat");
            when(requestBuilder.buildYouTubeRecipeRequest("My Cat Video", "Watch my cat do stuff", "Look at my cat"))
                    .thenReturn(mockRequest);
            when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                    .thenReturn(GeminiResponse.success(nonRecipe, "{\"is_recipe\": false}"));

            Transformer.Response response = transformer.transform(null, videoUrl);

            assertThat(response).isNotNull();
            assertThat(response.isRecipe()).isFalse();
            assertThat(response.recipe()).isEqualTo(nonRecipe);
        }

        @Test
        void throwsWhenSourceUrlIsNull() {
            assertThatThrownBy(() -> transformer.transform(null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sourceUrl cannot be null");
        }

        @Test
        void throwsWhenSourceUrlIsInvalid() {
            assertThatThrownBy(() -> transformer.transform(null, "https://google.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid YouTube URL");

            verify(youTubeDataService, never()).getMetadata(any());
        }

        @Test
        void throwsWhenYouTubeApiFails() {
            String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ"))
                    .thenThrow(new YouTubeApiException("Video not found: dQw4w9WgXcQ"));

            assertThatThrownBy(() -> transformer.transform(null, videoUrl))
                    .isInstanceOf(YouTubeApiException.class)
                    .hasMessageContaining("Video not found");

            verify(transcriptServiceRouter, never()).getTranscript(any());
            verify(geminiClient, never()).request(any(), any());
        }

        @Test
        void throwsWhenTranscriptFails() {
            String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
            YouTubeMetadata metadata = new YouTubeMetadata(
                    "Test Video", "Description", "Channel", Instant.now());

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ")).thenReturn(metadata);
            when(transcriptServiceRouter.getTranscript("dQw4w9WgXcQ"))
                    .thenThrow(new TranscriptException("All transcript providers failed"));

            assertThatThrownBy(() -> transformer.transform(null, videoUrl))
                    .isInstanceOf(TranscriptException.class)
                    .hasMessageContaining("All transcript providers failed");

            verify(geminiClient, never()).request(any(), any());
        }

        @Test
        void throwsClientExceptionWhenGeminiReturnsError() {
            String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
            YouTubeMetadata metadata = new YouTubeMetadata(
                    "Test Video", "Description", "Channel", Instant.now());
            GeminiRequest mockRequest = GeminiRequest.builder().build();

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ")).thenReturn(metadata);
            when(transcriptServiceRouter.getTranscript("dQw4w9WgXcQ")).thenReturn("transcript text");
            when(requestBuilder.buildYouTubeRecipeRequest("Test Video", "Description", "transcript text"))
                    .thenReturn(mockRequest);
            when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                    .thenReturn(GeminiResponse.failure(GeminiResponse.Code.BLOCKED, "Content blocked"));

            assertThatThrownBy(() -> transformer.transform(null, videoUrl))
                    .isInstanceOf(ClientException.class)
                    .hasMessageContaining("Gemini Client returned error code: BLOCKED");
        }

        @Test
        void throwsClientExceptionWhenGeminiReturnsOtherError() {
            String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
            YouTubeMetadata metadata = new YouTubeMetadata(
                    "Test Video", "Description", "Channel", Instant.now());
            GeminiRequest mockRequest = GeminiRequest.builder().build();

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ")).thenReturn(metadata);
            when(transcriptServiceRouter.getTranscript("dQw4w9WgXcQ")).thenReturn("transcript text");
            when(requestBuilder.buildYouTubeRecipeRequest("Test Video", "Description", "transcript text"))
                    .thenReturn(mockRequest);
            when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                    .thenReturn(GeminiResponse.failure(GeminiResponse.Code.OTHER, "Network error"));

            assertThatThrownBy(() -> transformer.transform(null, videoUrl))
                    .isInstanceOf(ClientException.class)
                    .hasMessageContaining("Gemini Client returned error code: OTHER");
        }

        @Test
        void delegatesToRequestBuilderForPromptFormatting() {
            String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
            YouTubeMetadata metadata = new YouTubeMetadata(
                    "My Recipe", "Flour and eggs", "Cook Channel", Instant.now());
            Recipe recipe = createTestRecipe("My Recipe", true);
            GeminiRequest mockRequest = GeminiRequest.builder().build();

            when(youTubeDataService.getMetadata("dQw4w9WgXcQ")).thenReturn(metadata);
            when(transcriptServiceRouter.getTranscript("dQw4w9WgXcQ")).thenReturn("Mix everything");
            when(requestBuilder.buildYouTubeRecipeRequest("My Recipe", "Flour and eggs", "Mix everything"))
                    .thenReturn(mockRequest);
            when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                    .thenReturn(GeminiResponse.success(recipe, "{}"));

            transformer.transform(null, videoUrl);

            verify(requestBuilder).buildYouTubeRecipeRequest("My Recipe", "Flour and eggs", "Mix everything");
            verify(geminiClient).request(eq(mockRequest), eq(Recipe.class));
        }
    }
}
