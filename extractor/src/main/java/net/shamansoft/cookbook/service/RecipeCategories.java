package net.shamansoft.cookbook.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The closed list of recipe categories the extractor may return, in English whatever the page's language.
 * Must match the {@code category} enum in {@code llm-recipe-schema.json}.
 * The page's own category words go in {@code tags}.
 */
public final class RecipeCategories {

    public static final List<String> ALLOWED = List.of(
            "breakfast", "lunch", "dinner", "main", "side", "soup", "salad",
            "appetizer", "snack", "dessert", "baking", "drink", "sauce");

    private static final Set<String> ALLOWED_SET = Set.copyOf(ALLOWED);

    private RecipeCategories() {
    }

    /**
     * Keeps only values on the closed list, lowercased and without duplicates, in their original order.
     *
     * @param values categories as extracted; may be null
     * @return the allowed categories, empty if none
     */
    public static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                String key = value.trim().toLowerCase(Locale.ROOT);
                if (ALLOWED_SET.contains(key)) {
                    result.add(key);
                }
            }
        }
        return List.copyOf(result);
    }
}
