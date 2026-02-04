package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for storing OAuth tokens
 */
@Data
public class OAuthTokenRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @Min(value = 0, message = "Expires in must be non-negative")
    private long expiresIn;
}
