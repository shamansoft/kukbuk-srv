package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformerResponseTest {

    @Test
    void notRecipe_hasZeroConfidenceAndEmptyRecipes() {
        Transformer.Response r = Transformer.Response.notRecipe();
        assertThat(r.isRecipe()).isFalse();
        assertThat(r.confidence()).isEqualTo(0.0);
        assertThat(r.recipes()).isEmpty();
        assertThat(r.recipe()).isNull();
    }

    @Test
    void notRecipe_withConfidence_setsExplicitConfidence() {
        Transformer.Response r = Transformer.Response.notRecipe(0.7);
        assertThat(r.isRecipe()).isFalse();
        assertThat(r.confidence()).isEqualTo(0.7);
    }

    @Test
    void recipe_singleRecipe_confidenceIsOne() {
        Recipe rec = buildRecipe();
        Transformer.Response r = Transformer.Response.recipe(rec);
        assertThat(r.isRecipe()).isTrue();
        assertThat(r.confidence()).isEqualTo(1.0);
        assertThat(r.recipe()).isEqualTo(rec);
    }

    @Test
    void recipe_nullThrows() {
        assertThatThrownBy(() -> Transformer.Response.recipe(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recipes_list_confidenceIsOne() {
        Recipe rec = buildRecipe();
        Transformer.Response r = Transformer.Response.recipes(List.of(rec));
        assertThat(r.isRecipe()).isTrue();
        assertThat(r.recipes()).hasSize(1);
    }

    @Test
    void recipes_emptyListThrows() {
        assertThatThrownBy(() -> Transformer.Response.recipes(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withRawResponse_setsAllFields() {
        Recipe rec = buildRecipe();
        Transformer.Response r = Transformer.Response.withRawResponse(true, 0.9, List.of(rec), "raw");
        assertThat(r.isRecipe()).isTrue();
        assertThat(r.confidence()).isEqualTo(0.9);
        assertThat(r.rawLlmResponse()).isEqualTo("raw");
    }

    @Test
    void withRawResponse_nullListDefaultsToEmpty() {
        Transformer.Response r = Transformer.Response.withRawResponse(false, 0.0, null, null);
        assertThat(r.recipes()).isEmpty();
    }

    private Recipe buildRecipe() {
        return new Recipe(true, "1.0.0", "1.0.0",
                new RecipeMetadata("Test", null, null, null, LocalDate.now(),
                        null, null, null, null, null, null, null, null),
                null,
                List.of(new Ingredient("flour", "1", "cup", null, false, null, null)),
                null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }
}
