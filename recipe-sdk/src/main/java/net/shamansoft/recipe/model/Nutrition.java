package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

/**
 * Represents nutritional information for a recipe.
 *
 * @param servingSize Serving size description
 * @param calories Calories per serving
 * @param protein Protein in grams
 * @param carbohydrates Carbohydrates in grams
 * @param fat Fat in grams
 * @param fiber Fiber in grams
 * @param sugar Sugar in grams
 * @param sodium Sodium in milligrams
 * @param notes Additional notes about nutritional information
 */
public record Nutrition(
        @JsonProperty("serving_size")
        String servingSize,

        @Min(0)
        @JsonProperty("calories")
        Integer calories,

        @Min(0)
        @JsonProperty("protein")
        Double protein,

        @Min(0)
        @JsonProperty("carbohydrates")
        Double carbohydrates,

        @Min(0)
        @JsonProperty("fat")
        Double fat,

        @Min(0)
        @JsonProperty("fiber")
        Double fiber,

        @Min(0)
        @JsonProperty("sugar")
        Double sugar,

        @Min(0)
        @JsonProperty("sodium")
        Double sodium,

        @JsonProperty("notes")
        String notes
) {
}
