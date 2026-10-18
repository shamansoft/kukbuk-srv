package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Storage;
import net.shamansoft.recipe.model.Substitution;

import java.util.List;

/**
 * Gemini's answer on the JSON-LD path ({@code llm-enrichment-schema.json}): what the page's JSON-LD cannot give in
 * code. Each ingredient row names the JSON-LD {@code recipeIngredient} line it was parsed from, so code can check
 * that no line was dropped or repeated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonLdEnrichment(
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("language") String language,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("servings") Integer servings,
        @JsonProperty("prep_time") String prepTime,
        @JsonProperty("cook_time") String cookTime,
        @JsonProperty("total_time") String totalTime,
        @JsonProperty("ingredients") List<Row> ingredients,
        @JsonProperty("instructions") List<Instruction> instructions,
        @JsonProperty("equipment") List<String> equipment,
        @JsonProperty("notes") String notes,
        @JsonProperty("storage") Storage storage
) {
    public JsonLdEnrichment {
        if (ingredients == null) ingredients = List.of();
        if (instructions == null) instructions = List.of();
    }

    /**
     * One ingredient row, or a section header row.
     *
     * @param sourceIndex the JSON-LD line it comes from; null for an ingredient only the recipe card lists
     * @param header      true when the line is a section header, not an ingredient
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("source_index") Integer sourceIndex,
            @JsonProperty("header") Boolean header,
            @JsonProperty("item") String item,
            @JsonProperty("amount") String amount,
            @JsonProperty("unit") String unit,
            @JsonProperty("notes") String notes,
            @JsonProperty("optional") Boolean optional,
            @JsonProperty("substitutions") List<Substitution> substitutions,
            @JsonProperty("component") String component
    ) {
        public boolean isHeader() {
            return Boolean.TRUE.equals(header);
        }
    }
}
