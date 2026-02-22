package net.shamansoft.cookbook.dto;

/**
 * Represents a single extracted and uploaded recipe in a multi-recipe response.
 */
public record RecipeItemResult(
        String title,
        String driveFileId,
        String driveFileUrl
) {
}
