package net.shamansoft.cookbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for storage connection/disconnection operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageConnectionResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private String status;      // "success" or "error"
    private String message;     // Human-readable message
    private boolean connected;  // Current connection state

    // Folder information (populated when connecting)
    private String defaultFolderId;      // Google Drive folder ID
    private String defaultFolderName;    // Human-readable folder name

    /**
     * Factory method for successful operations
     */
    public static StorageConnectionResponse success(String message, boolean connected) {
        return StorageConnectionResponse.builder()
                .timestamp(LocalDateTime.now())
                .status("success")
                .message(message)
                .connected(connected)
                .build();
    }

    /**
     * Factory method for successful operations with folder information
     */
    public static StorageConnectionResponse success(String message, boolean connected,
                                                    String defaultFolderId, String defaultFolderName) {
        return StorageConnectionResponse.builder()
                .timestamp(LocalDateTime.now())
                .status("success")
                .message(message)
                .connected(connected)
                .defaultFolderId(defaultFolderId)
                .defaultFolderName(defaultFolderName)
                .build();
    }

    /**
     * Factory method for error responses
     */
    public static StorageConnectionResponse error(String message) {
        return StorageConnectionResponse.builder()
                .timestamp(LocalDateTime.now())
                .status("error")
                .message(message)
                .connected(false)
                .build();
    }
}
