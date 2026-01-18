package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.shamansoft.cookbook.service.ResourcesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestBuilderTest {

    @Mock
    private ResourcesLoader resourcesLoader;
    private RequestBuilder requestBuilder;

    @BeforeEach
    void setUp() throws IOException {
        requestBuilder = new RequestBuilder(resourcesLoader, new ObjectMapper());
        when(resourcesLoader.loadYaml("classpath:prompt.md")).thenReturn(
                "Formatted prompt with %s, %s, %s, and %s");
        when(resourcesLoader.loadYaml("classpath:recipe-schema-1.0.0.json"))
                .thenReturn("schema");
        when(resourcesLoader.loadYaml("classpath:example.yaml"))
                .thenReturn("example");
        requestBuilder.init();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>Valid content</html>", "<html>Another content</html>"})
    void buildsBodyStringWithValidHtml(String htmlContent) throws Exception {
        String expectedPrompt = "Formatted prompt with %s, %s, %s, and %s";

        String result = requestBuilder.buildBodyString(htmlContent);

        assertThat(result).contains("\"text\":\"" + expectedPrompt.formatted(
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "schema", "example", htmlContent) + "\"");
        assertThat(result).contains("\"temperature\":");
        assertThat(result).contains("\"topP\":");
        assertThat(result).contains("\"maxOutputTokens\":4096");
        assertThat(result).contains("\"safetySettings\":");
    }

    @Test
    void throwsExceptionWhenHtmlContentIsNull() throws IOException {
        when(resourcesLoader.loadYaml("classpath:prompt.md")).thenReturn(null);
        requestBuilder.init();

        assertThatThrownBy(() -> requestBuilder.buildBodyString(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildsBodyStringWithEmptyHtmlContent() throws Exception {

        String result = requestBuilder.buildBodyString("");

        assertThat(result).contains("\"text\":\"" + "Formatted prompt with " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ", schema, example, and \"");
        assertThat(result).contains("\"temperature\":");
        assertThat(result).contains("\"topP\":");
        assertThat(result).contains("\"maxOutputTokens\":4096");
        assertThat(result).contains("\"safetySettings\":");
    }

    @Test
    void initializesResourcesCorrectly() throws Exception {
        String expectedPrompt = "Formatted prompt with %s, %s, %s, and %s";
        assertThat(requestBuilder.withHtml("HTML content")).isEqualTo(expectedPrompt.formatted(
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "schema", "example", "HTML content"));
    }

    @Test
    void buildBodyStringWithFeedback_shouldIncludeFeedbackInPrompt() throws Exception {
        String htmlContent = "<html>Recipe</html>";
        String previousYaml = "invalid: yaml\nmissing: fields";
        String validationError = "Missing required field: schema_version";

        String result = requestBuilder.buildBodyStringWithFeedback(
                htmlContent, previousYaml, validationError);

        assertThat(result).contains("VALIDATION FEEDBACK");
        assertThat(result).contains("invalid: yaml");
        assertThat(result).contains("missing: fields");
        assertThat(result).contains(validationError);
        assertThat(result).contains("\"temperature\":");
        assertThat(result).contains("\"maxOutputTokens\":4096");

        // Verify new markdown fence reminder (lines 59-60)
        assertThat(result).contains("DO NOT wrap it in markdown code fences (```yaml)");
        assertThat(result).contains("Start your response directly with 'schema_version:'");
    }

    @Test
    void withHtmlAndFeedback_shouldConstructProperFeedbackPrompt() {
        String htmlContent = "<html>Recipe</html>";
        String previousYaml = "schema_version: \"1.0.0\"\ninvalid: data";
        String validationError = "Field 'metadata' is required";

        String result = requestBuilder.withHtmlAndFeedback(
                htmlContent, previousYaml, validationError);

        assertThat(result).contains("---VALIDATION FEEDBACK---");
        assertThat(result).contains("Your previous attempt produced YAML that failed validation:");
        assertThat(result).contains("Previous YAML (for reference):");
        assertThat(result).contains(previousYaml);
        assertThat(result).contains("Validation Error:");
        assertThat(result).contains(validationError);
        assertThat(result).contains("IMPORTANT: Please correct these issues");
        assertThat(result).contains("REMINDER: Output ONLY the YAML content");
    }

    @Test
    void buildBodyStringWithFeedback_shouldProduceValidJson() throws Exception {
        String htmlContent = "<html>Recipe</html>";
        String previousYaml = "test: yaml";
        String validationError = "Some error";

        String result = requestBuilder.buildBodyStringWithFeedback(
                htmlContent, previousYaml, validationError);

        // Verify it's valid JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(result);
        assertThat(jsonNode.has("contents")).isTrue();
        assertThat(jsonNode.has("generationConfig")).isTrue();
        assertThat(jsonNode.has("safetySettings")).isTrue();
    }

}