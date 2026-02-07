package net.shamansoft.cookbook.dto;

public record YouTubeJobStatusResponse(
        String jobId,
        String status,
        String driveFileId,
        String driveFileUrl,
        String errorMessage
) {
}
