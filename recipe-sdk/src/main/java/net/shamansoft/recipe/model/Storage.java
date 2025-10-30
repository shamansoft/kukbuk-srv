package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents storage instructions for a recipe.
 *
 * @param refrigerator How long the recipe can be stored in the refrigerator
 * @param freezer How long the recipe can be stored in the freezer
 * @param roomTemperature How long the recipe can be stored at room temperature
 */
public record Storage(
        @JsonProperty("refrigerator")
        String refrigerator,

        @JsonProperty("freezer")
        String freezer,

        @JsonProperty("room_temperature")
        String roomTemperature
) {
}
