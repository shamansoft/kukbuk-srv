package net.shamansoft.cookbook.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record RecipeResponse(
        String url,
        String title,
        String driveFileId,     // first recipe's Drive file ID (backward compat)
        String driveFileUrl,    // first recipe's Drive file URL (backward compat)
        Boolean isRecipe,
        List<RecipeItemResult> recipes  // all uploaded recipes (multi-recipe support)
) {
}
