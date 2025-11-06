package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents a substitution for an ingredient.
 *
 * @param item Name of the substitute ingredient
 * @param amount Amount of the substitute (can be numeric or descriptive)
 * @param unit Unit of measurement
 * @param notes Additional notes about the substitution
 * @param ratio Conversion ratio like "1:1" or "2:1"
 */
public record Substitution(
        @NotBlank
        @JsonProperty("item")
        String item,

        @JsonProperty("amount")
        String amount,

        @JsonProperty("unit")
        String unit,

        @JsonProperty("notes")
        String notes,

        @JsonProperty("ratio")
        String ratio
) {
}
