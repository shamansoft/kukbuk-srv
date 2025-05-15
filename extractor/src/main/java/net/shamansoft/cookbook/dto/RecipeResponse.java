package net.shamansoft.cookbook.dto;

import lombok.Builder;

@Builder
public record RecipeResponse(String url, String title, String raw, String content) {
}
