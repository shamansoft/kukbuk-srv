package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import net.shamansoft.recipe.parser.RecipeSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EncodingReproductionTest {

    @Test
    void shouldPreserveSmartQuotesInYaml() throws Exception {
        RecipeSerializer serializer = new RecipeSerializer();

        Recipe recipe = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata("Test Recipe", "src", null, "en", null, null, null, null, null, null, null, null,
                        null),
                "They won’t rise if you don’t dimple them.",
                List.of(),
                List.of(),
                List.of(),
                null,
                "",
                null);

        String yaml = serializer.serialize(recipe);
        System.out.println("Generated YAML:\n" + yaml);

        assertThat(yaml).contains("won’t");
        assertThat(yaml).contains("don’t");
        assertThat(yaml).doesNotContain("won?t");
    }
}
