package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for connecting Google Drive storage.
 * Mobile app sends authorization code which backend exchanges for tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageConnectionRequest {

    @NotBlank(message = "Authorization code is required")
    private String authorizationCode;

    @NotNull(message = "Redirect URI is required")
    private String redirectUri;

    // Optional - can be set later via separate endpoint if needed
    private String defaultFolderId;
}
