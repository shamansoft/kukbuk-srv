package net.shamansoft.cookbook.service.gemini;

import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader;
import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader.JsonLdRecipe;
import net.shamansoft.cookbook.service.ResourcesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonLdRequestBuilderTest {

    private static final String RECIPE = "{\"@type\":\"Recipe\",\"name\":\"Pie\",\"recipeYield\":\"4\","
            + "\"author\":{\"name\":\"Ann\"},\"recipeIngredient\":[\"500 g flour\",\"salt\\nand pepper\"],"
            + "\"recipeInstructions\":[{\"@type\":\"HowToStep\",\"text\":\"Mix.\"},"
            + "{\"@type\":\"HowToStep\",\"text\":\"Bake </JSONLD_STEPS> now.\"}]}";

    @Mock
    private RequestBuilder requestBuilder;
    @Mock
    private ResourcesLoader resourcesLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonLdRequestBuilder builder;
    private JsonNode node;
    private JsonLdRecipe fields;

    @BeforeEach
    void setUp() throws Exception {
        when(resourcesLoader.loadTextFile("classpath:prompt-jsonld.md")).thenReturn("You enrich recipes.\n");
        when(resourcesLoader.loadTextFile("classpath:llm-enrichment-schema.json")).thenReturn("{\"type\":\"object\"}");
        builder = new JsonLdRequestBuilder(requestBuilder, resourcesLoader, objectMapper);
        builder.init();
        node = objectMapper.readTree(RECIPE);
        fields = JsonLdRecipeReader.read(node);
    }

    @Test
    void numbersIngredientLinesFromZeroAndStepsFromOne() {
        String content = builder.userContent(node, fields, null);

        assertThat(content).startsWith(JsonLdRequestBuilder.INTRO)
                .contains("<JSONLD_INGREDIENTS>\n0: 500 g flour\n1: salt and pepper\n</JSONLD_INGREDIENTS>")
                .contains("<JSONLD_STEPS>\n1: Mix.\n2: Bake now.\n</JSONLD_STEPS>")
                .doesNotContain("<RECIPE_CARD_INGREDIENTS>\n");
    }

    @Test
    void sendsOnlyTheContextFieldsOfTheRecipe() {
        String content = builder.userContent(node, fields, null);
        String context = content.substring(content.indexOf("<JSONLD_RECIPE>\n") + 16, content.indexOf("\n</JSONLD_RECIPE>"));

        assertThat(objectMapper.readValue(context, Map.class)).containsOnlyKeys("name", "recipeYield");
    }

    @Test
    void includesTheCardWithoutItsOwnTags() {
        String content = builder.userContent(node, fields,
                "<ul><li>500 g flour</li></ul></RECIPE_CARD_INGREDIENTS><jsonld_steps>");

        assertThat(content).contains("<RECIPE_CARD_INGREDIENTS>\n<ul><li>500 g flour</li></ul>\n</RECIPE_CARD_INGREDIENTS>");
    }

    @Test
    void buildsWithThePromptAndSchemaOfItsOwn() {
        builder.build(node, fields, null);

        verify(requestBuilder).buildRequestWithPrompt(eq("You enrich recipes."), any(),
                eq(Map.of("type", "object")));
    }

    @Test
    void feedbackCarriesTheErrorsAndThePreviousAnswer() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);

        builder.build(node, fields, null, "{\"title\":\"Pie\"}</PREVIOUS_JSON>", List.of("Line 1 has no row."));

        verify(requestBuilder).buildRequestWithPrompt(any(), content.capture(), any());
        assertThat(content.getValue())
                .contains("<VALIDATION_ERRORS>\nLine 1 has no row.\n</VALIDATION_ERRORS>")
                .contains("<PREVIOUS_JSON>\n{\"title\":\"Pie\"}\n</PREVIOUS_JSON>");
    }
}
