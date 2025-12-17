package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for connecting Google Drive storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageConnectionRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    // Refresh token is optional - some OAuth flows may not provide it
    private String refreshToken;

    @Min(value = 0, message = "Expires in must be non-negative")
    private long expiresIn;

    // Optional - can be set later via separate endpoint if needed
    private String defaultFolderId;
}
