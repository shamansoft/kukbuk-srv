package net.shamansoft.cookbook.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record YouTubeJob(
        String jobId,
        String userId,
        String videoUrl,
        JobStatus status,
        Instant createdAt,
        Instant completedAt,
        String driveFileId,
        String errorMessage
) {

    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("jobId", jobId);
        data.put("userId", userId);
        data.put("videoUrl", videoUrl);
        data.put("status", status != null ? status.name() : null);
        if (createdAt != null) {
            data.put("createdAt", createdAt.toString());
        }
        if (completedAt != null) {
            data.put("completedAt", completedAt.toString());
        }
        if (driveFileId != null) {
            data.put("driveFileId", driveFileId);
        }
        if (errorMessage != null) {
            data.put("errorMessage", errorMessage);
        }
        return data;
    }

    public static Optional<YouTubeJob> fromMap(Map<String, Object> data) {
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(new YouTubeJob(
                (String) data.get("jobId"),
                (String) data.get("userId"),
                (String) data.get("videoUrl"),
                data.get("status") != null ? JobStatus.valueOf((String) data.get("status")) : null,
                parseInstant(data.get("createdAt")),
                parseInstant(data.get("completedAt")),
                (String) data.get("driveFileId"),
                (String) data.get("errorMessage")
        ));
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return Instant.parse(s);
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }

    public YouTubeJob withStatus(JobStatus newStatus) {
        return new YouTubeJob(jobId, userId, videoUrl, newStatus, createdAt, completedAt, driveFileId, errorMessage);
    }

    public YouTubeJob withCompleted(String driveFileId) {
        return new YouTubeJob(jobId, userId, videoUrl, JobStatus.COMPLETED, createdAt, Instant.now(), driveFileId, null);
    }

    public YouTubeJob withFailed(String errorMessage) {
        return new YouTubeJob(jobId, userId, videoUrl, JobStatus.FAILED, createdAt, Instant.now(), driveFileId, errorMessage);
    }
}
