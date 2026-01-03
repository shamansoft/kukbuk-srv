package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for GET /v1/recipes endpoint.
 * Contains paginated list of fully-parsed recipes.
 */
@Data
@Builder
public class RecipeListResponse {
    private List<RecipeDto> recipes;
    private String nextPageToken;  // Google Drive's opaque pagination token
    private int count;  // Number of recipes in current page
}
