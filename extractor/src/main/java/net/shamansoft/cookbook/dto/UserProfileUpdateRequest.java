package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating user profile.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

    /**
     * User's display name.
     * Optional - if provided, updates the display name.
     */
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    /**
     * User's email address.
     * Optional - typically managed by OAuth, but can be updated if needed.
     */
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
}
