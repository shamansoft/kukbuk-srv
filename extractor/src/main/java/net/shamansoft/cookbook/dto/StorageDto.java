package net.shamansoft.cookbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.Instant;

/**
 * DTO for storage information in user profile response.
 * Represents nested storage object with connection details.
 */
@Builder
public record StorageDto(
        String type,
        String folderId,
        String folderName,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant connectedAt) {
}
