package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

}