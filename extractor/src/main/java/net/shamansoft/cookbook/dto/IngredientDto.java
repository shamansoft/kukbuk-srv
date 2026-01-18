package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Ingredient DTO for mobile API.
 * Maps from Recipe SDK's Ingredient model.
 */
@Data
@Builder
public class IngredientDto {
    private String name;
    private String amount;
    private String unit;
    private String notes;
    private Boolean optional;
    private String component;
}
