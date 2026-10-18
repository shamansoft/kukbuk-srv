package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader;
import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader.JsonLdRecipe;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.cookbook.html.strategy.StructuredDataStrategy;
import net.shamansoft.recipe.model.CoverImage;
import net.shamansoft.recipe.model.Nutrition;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * On the JSON-LD path, replaces the model's title, servings, times, cover image and nutrition with the values the
 * page publishes in its schema.org {@code Recipe}, field by field where the JSON-LD has one. The model rewords or
 * drops these even when it has them.
 *
 * <p>Interim: removed when the recipe is assembled from the JSON-LD directly (story 9, task 5.1).
 */
@Service
@Slf4j
public class JsonLdFieldOverwriter {

    private static final Pattern SITE_SUFFIX = Pattern.compile(
            "\\s*(?:[-–—|:]|by\\b|recipes?\\b|rezept\\b|receita\\b|recette\\b|ricetta\\b|receta\\b|рецепт)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ObjectMapper objectMapper;

    @Value("${recipe.jsonld-fields.enabled:true}")
    private boolean enabled;

    public JsonLdFieldOverwriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Runs after validation and post-processing, so every value it sets is already in its stored form.
     *
     * @param response  the model's answer for {@code cleaned}
     * @param cleaned   the content the model was given
     * @param sourceUrl the page URL, to resolve a relative JSON-LD image
     * @return the response with JSON-LD fields applied, or unchanged when the content was not JSON-LD, the answer
     * has more than one recipe, or its one recipe is not the JSON-LD's (another recipe of the page survived
     * validation alone)
     */
    public Transformer.Response apply(Transformer.Response response, HtmlCleaner.Results cleaned, String sourceUrl) {
        if (!enabled || !response.isRecipe() || response.recipes().size() != 1
                || cleaned.strategyUsed() != Strategy.STRUCTURED_DATA) {
            return response;
        }
        JsonLdRecipe fields;
        try {
            fields = JsonLdRecipeReader.read(objectMapper.readTree(StructuredDataStrategy.jsonLd(cleaned.cleanedHtml())));
        } catch (Exception e) {
            log.debug("Could not read JSON-LD fields, keeping the model's", e);
            return response;
        }
        if (!describes(fields, response.recipe())) {
            log.debug("Recipe does not match the JSON-LD ingredients, keeping the model's fields");
            return response;
        }
        return Transformer.Response.withRawResponse(true, response.confidence(),
                List.of(overwrite(response.recipe(), fields, sourceUrl)), response.rawLlmResponse());
    }

    // At least half of the JSON-LD ingredient lines show up among the recipe's ingredients
    private static boolean describes(JsonLdRecipe fields, Recipe recipe) {
        List<String> keys = JsonLdRecipeReader.keyWords(fields.ingredients());
        if (keys.isEmpty() || recipe.ingredients() == null) return keys.isEmpty();
        String items = recipe.ingredients().stream()
                .map(i -> i.item() + " " + Objects.toString(i.notes(), ""))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        return keys.stream().filter(items::contains).count() * 2 >= keys.size();
    }

    Recipe overwrite(Recipe recipe, JsonLdRecipe fields, String sourceUrl) {
        RecipeMetadata m = recipe.metadata();
        if (m == null) return recipe;
        // resolved as RecipePostProcessor resolves the model's; an unresolvable path keeps the model's cover
        String image = ImageUrlResolver.resolve(fields.image(), sourceUrl).orElse(null);
        CoverImage cover = image == null ? m.coverImage()
                : new CoverImage(image, m.coverImage() != null ? m.coverImage().alt() : null);
        RecipeMetadata metadata = new RecipeMetadata(
                title(fields.title(), m.title()),
                m.source(),
                m.author(),
                m.language(),
                m.dateCreated(),
                m.category(),
                m.tags(),
                or(fields.servings(), m.servings()),
                or(fields.prepTime(), m.prepTime()),
                or(fields.cookTime(), m.cookTime()),
                or(fields.totalTime(), m.totalTime()),
                m.difficulty(),
                cover);
        return new Recipe(
                recipe.isRecipe(),
                recipe.schemaVersion(),
                recipe.recipeVersion(),
                metadata,
                recipe.description(),
                recipe.ingredients(),
                recipe.equipment(),
                recipe.instructions(),
                nutrition(recipe.nutrition(), fields.nutrition()),
                recipe.notes(),
                recipe.storage());
    }

    // A name the model only trimmed ("Creamy Lemon Chicken Recipe by Tasty" -> "Creamy Lemon Chicken") keeps the trim
    private static String title(String page, String model) {
        if (page == null) return model;
        if (model == null || model.isBlank()) return page;
        String trimmed = model.trim();
        boolean trimmedSuffix = page.regionMatches(true, 0, trimmed, 0, trimmed.length())
                && SITE_SUFFIX.matcher(page.substring(trimmed.length())).lookingAt();
        return trimmedSuffix ? model : page;
    }

    private static Nutrition nutrition(Nutrition model, Nutrition page) {
        if (page == null) return model;
        if (model == null) return page;
        return new Nutrition(
                or(page.servingSize(), model.servingSize()),
                or(page.calories(), model.calories()),
                or(page.protein(), model.protein()),
                or(page.carbohydrates(), model.carbohydrates()),
                or(page.fat(), model.fat()),
                or(page.fiber(), model.fiber()),
                or(page.sugar(), model.sugar()),
                or(page.sodium(), model.sodium()),
                model.notes());
    }

    private static <T> T or(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
