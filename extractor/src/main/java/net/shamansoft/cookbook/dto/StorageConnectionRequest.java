package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
