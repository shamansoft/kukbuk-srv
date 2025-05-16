package net.shamansoft.cookbook.dto;

import lombok.Builder;

@Builder
public record RecipeResponse(
        String url,
        String title,
        String raw,
        String content,
        /**
         * ID of the file stored in Google Drive; may be null if not persisted.
         */
        String driveFileId,
        /**
         * URL of the file in Google Drive; may be null if not persisted.
         */
        String driveFileUrl
) {
}
