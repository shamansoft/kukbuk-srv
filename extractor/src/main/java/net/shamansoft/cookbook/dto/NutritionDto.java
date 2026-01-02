package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Nutritional information DTO for mobile API.
 * Maps from Recipe SDK's Nutrition model.
 */
@Data
@Builder
public class NutritionDto {
    private String servingSize;
    private Integer calories;
    private Double protein;
    private Double carbohydrates;
    private Double fat;
    private Double fiber;
    private Double sugar;
    private Double sodium;
    private String notes;
}
