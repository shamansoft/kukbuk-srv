package net.shamansoft.cookbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO for user profile endpoint.
 * Contains basic user information and optional storage configuration.
 */
@Builder
public record UserProfileResponseDto(
        String userId,
        String email,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        StorageDto storage) {
}
