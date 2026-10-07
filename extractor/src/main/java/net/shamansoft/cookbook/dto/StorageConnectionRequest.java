package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.shamansoft.cookbook.validation.ValidFolderName;

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

    /**
     * Google Drive folder name (not ID).
     * If null or empty, the default folder name from cookbook.drive.folder-name config will be used.
     * The folder will be created if it doesn't exist.
     */
    @ValidFolderName
    private String folderName;

    /**
     * PKCE code verifier (RFC 7636) for providers whose authorize page the client opens itself
     * (Dropbox). Optional: when the client sent a code_challenge, the provider rejects the exchange
     * without the matching verifier.
     */
    @Pattern(regexp = "^[A-Za-z0-9._~-]{43,128}$", message = "Invalid PKCE code verifier")
    private String codeVerifier;
}
