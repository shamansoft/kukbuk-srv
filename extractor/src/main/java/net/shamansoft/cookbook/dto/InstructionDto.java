package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Instruction step DTO for mobile API.
 * Maps from Recipe SDK's Instruction model.
 */
@Data
@Builder
public class InstructionDto {
    private Integer step;
    private String description;
    private String time;
    private String temperature;
}
