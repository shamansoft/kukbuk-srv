package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.CleanupService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiRestTransformerTest {

    @Mock
    private WebClient geminiWebClient;

    @Mock
    private RequestBuilder requestBuilder;

    @Mock
    private CleanupService cleanupService;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ObjectMapper testObjectMapper = new ObjectMapper();

    @InjectMocks
    private GeminiRestTransformer geminiRestTransformer;

    @BeforeEach
    void setUp() throws IOException {
        // Setup common WebClient mocking
        when(geminiWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // Mock requestBuilder to return non-null body
        // Use lenient() since transformWithFeedback test uses buildBodyStringWithFeedback instead
        lenient().when(requestBuilder.buildBodyString(anyString())).thenReturn("{}");
    }

    @Test
    void transformReturnsValidResponseWhenCandidatesExist() {
        String htmlContent = "<html>Test content</html>";
        String yamlContent = "is_recipe: true\ntitle: Valid Recipe";

        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        ObjectNode contentNode = testObjectMapper.createObjectNode();
        ObjectNode partNode = testObjectMapper.createObjectNode();

        partNode.put("text", yamlContent);
        contentNode.set("parts", testObjectMapper.createArrayNode().add(partNode));
        candidateNode.set("content", contentNode);
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform(htmlContent);

        assertThat(result.isRecipe()).isTrue();
        assertThat(result.value()).isEqualTo(yamlContent);
    }

    @Test
    void transformThrowsExceptionWhenResponseIsNull() {
        String htmlContent = "<html>Test content</html>";

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> geminiRestTransformer.transform(htmlContent))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API");
    }

    @Test
    void transformThrowsExceptionWhenCandidatesAreMissing() {
        String htmlContent = "<html>Test content</html>";
        ObjectNode responseNode = testObjectMapper.createObjectNode();

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        assertThatThrownBy(() -> geminiRestTransformer.transform(htmlContent))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API");
    }

    @Test
    void transformReturnsNonRecipeWhenYamlIndicatesFalse() {
        String htmlContent = "<html>Test content</html>";
        String yamlContent = "is_recipe: false\ntitle: Not a Recipe";

        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        ObjectNode contentNode = testObjectMapper.createObjectNode();
        ObjectNode partNode = testObjectMapper.createObjectNode();

        partNode.put("text", yamlContent);
        contentNode.set("parts", testObjectMapper.createArrayNode().add(partNode));
        candidateNode.set("content", contentNode);
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform(htmlContent);

        assertThat(result.isRecipe()).isFalse();
        assertThat(result.value()).isEqualTo(yamlContent);
    }

    @Test
    void transformHandlesMalformedCandidateContent() {
        String htmlContent = "<html>Test content</html>";

        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        candidateNode.set("content", testObjectMapper.createObjectNode()); // Missing parts
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        assertThatThrownBy(() -> geminiRestTransformer.transform(htmlContent))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API");
    }

    @Test
    void transformWithFeedback_shouldCallGeminiWithFeedback() throws IOException {
        String htmlContent = "<html>Recipe</html>";
        String previousYaml = "invalid: yaml";
        String validationError = "Missing required fields";
        String correctedYaml = "schema_version: \"1.0.0\"\ntitle: Fixed";

        // Stub the feedback method for this test
        when(requestBuilder.buildBodyStringWithFeedback(anyString(), anyString(), anyString())).thenReturn("{}");

        ObjectNode responseNode = createBasicResponse(correctedYaml);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(correctedYaml)).thenReturn(correctedYaml);

        Transformer.Response result = geminiRestTransformer.transformWithFeedback(
                htmlContent, previousYaml, validationError);

        assertThat(result.isRecipe()).isTrue();
        verify(requestBuilder).buildBodyStringWithFeedback(eq(htmlContent), eq(previousYaml), eq(validationError));
    }

    @Test
    void transform_shouldLogFinishReason_whenPresent() {
        String yamlContent = "schema_version: \"1.0.0\"";
        ObjectNode responseNode = createResponseWithFinishReason(yamlContent, "STOP");

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.isRecipe()).isTrue();
    }

    @Test
    void transform_shouldWarnOnTruncation_whenFinishReasonIsNotStop() {
        String yamlContent = "schema_version: \"1.0.0\"";
        ObjectNode responseNode = createResponseWithFinishReason(yamlContent, "MAX_TOKENS");

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.isRecipe()).isTrue();
    }

    @Test
    void transform_shouldLogSafetyRatings_whenPresent() {
        String yamlContent = "schema_version: \"1.0.0\"";
        ObjectNode responseNode = createBasicResponse(yamlContent);

        // Add safety ratings
        ObjectNode candidateNode = (ObjectNode) responseNode.get("candidates").get(0);
        ArrayNode safetyRatings = testObjectMapper.createArrayNode();
        ObjectNode rating = testObjectMapper.createObjectNode();
        rating.put("category", "HARM_CATEGORY_HATE_SPEECH");
        rating.put("probability", "NEGLIGIBLE");
        safetyRatings.add(rating);
        candidateNode.set("safetyRatings", safetyRatings);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.isRecipe()).isTrue();
    }

    @Test
    void transform_shouldLogPromptFeedback_whenPresent() {
        String yamlContent = "schema_version: \"1.0.0\"";
        ObjectNode responseNode = createBasicResponse(yamlContent);

        ObjectNode promptFeedback = testObjectMapper.createObjectNode();
        promptFeedback.put("safetyRatings", "info");
        responseNode.set("promptFeedback", promptFeedback);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.isRecipe()).isTrue();
    }

    @Test
    void transform_shouldLogBlockReason_whenResponseIsBlocked() {
        String yamlContent = "schema_version: \"1.0.0\"";
        ObjectNode responseNode = createBasicResponse(yamlContent);

        ObjectNode promptFeedback = testObjectMapper.createObjectNode();
        promptFeedback.put("blockReason", "SAFETY");
        responseNode.set("promptFeedback", promptFeedback);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(yamlContent)).thenReturn(yamlContent);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.isRecipe()).isTrue();
    }

    @Test
    void transform_shouldConcatenateMultipleParts() {
        String part1 = "schema_version: \"1.0.0\"\n";
        String part2 = "title: Recipe\n";
        String part3 = "description: Test";
        String expectedYaml = part1 + part2 + part3;

        ObjectNode responseNode = createResponseWithMultipleParts(part1, part2, part3);

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(expectedYaml)).thenReturn(expectedYaml);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.value()).isEqualTo(expectedYaml);
    }

    @Test
    void transform_shouldSkipPartsWithoutTextField() {
        String validText = "schema_version: \"1.0.0\"";

        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        ObjectNode contentNode = testObjectMapper.createObjectNode();

        ArrayNode partsArray = testObjectMapper.createArrayNode();
        ObjectNode partWithText = testObjectMapper.createObjectNode();
        partWithText.put("text", validText);
        ObjectNode partWithoutText = testObjectMapper.createObjectNode();
        partWithoutText.put("other", "value"); // No "text" field
        partsArray.add(partWithText).add(partWithoutText);

        contentNode.set("parts", partsArray);
        candidateNode.set("content", contentNode);
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(validText)).thenReturn(validText);

        Transformer.Response result = geminiRestTransformer.transform("<html>Recipe</html>");

        assertThat(result.value()).isEqualTo(validText);
    }

    // Helper methods
    private ObjectNode createBasicResponse(String yamlContent) {
        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        ObjectNode contentNode = testObjectMapper.createObjectNode();
        ObjectNode partNode = testObjectMapper.createObjectNode();
        partNode.put("text", yamlContent);
        contentNode.set("parts", testObjectMapper.createArrayNode().add(partNode));
        candidateNode.set("content", contentNode);
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));
        return responseNode;
    }

    private ObjectNode createResponseWithFinishReason(String yamlContent, String finishReason) {
        ObjectNode responseNode = createBasicResponse(yamlContent);
        ObjectNode candidateNode = (ObjectNode) responseNode.get("candidates").get(0);
        candidateNode.put("finishReason", finishReason);
        return responseNode;
    }

    private ObjectNode createResponseWithMultipleParts(String... parts) {
        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        ObjectNode contentNode = testObjectMapper.createObjectNode();

        ArrayNode partsArray = testObjectMapper.createArrayNode();
        for (String part : parts) {
            ObjectNode partNode = testObjectMapper.createObjectNode();
            partNode.put("text", part);
            partsArray.add(partNode);
        }

        contentNode.set("parts", partsArray);
        candidateNode.set("content", contentNode);
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));
        return responseNode;
    }

}