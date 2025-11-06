package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Represents a recipe ingredient.
 *
 * @param item Name of the ingredient (required)
 * @param amount Amount of the ingredient (can be numeric like "2" or descriptive like "handful", "3-4")
 * @param unit Unit of measurement
 * @param notes Additional notes (preparation details, alternatives, "to taste", etc.)
 * @param optional Whether this ingredient is optional (e.g., garnishes)
 * @param substitutions List of possible substitutions for this ingredient
 * @param component Grouping identifier (e.g., "dough", "filling", "sauce", "main")
 */
public record Ingredient(
        @NotBlank
        @JsonProperty("item")
        String item,

        @JsonProperty("amount")
        String amount,

        @JsonProperty("unit")
        String unit,

        @JsonProperty("notes")
        String notes,

        @JsonProperty("optional")
        Boolean optional,

        @Valid
        @JsonProperty("substitutions")
        List<Substitution> substitutions,

        @JsonProperty("component")
        String component
) {
    public Ingredient {
        if (optional == null) {
            optional = false;
        }
        if (component == null) {
            component = "main";
        }
    }
}
