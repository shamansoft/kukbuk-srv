package net.shamansoft.cookbook.service;

import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "cookbook.tasks.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class CloudTasksService {

    private final CloudTasksClient cloudTasksClient;
    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String location;
    private final String queueName;
    private final String serviceUrl;

    public CloudTasksService(
            CloudTasksClient cloudTasksClient,
            ObjectMapper objectMapper,
            @Value("${cookbook.tasks.project-id}") String projectId,
            @Value("${cookbook.tasks.location}") String location,
            @Value("${cookbook.tasks.queue-name}") String queueName,
            @Value("${cookbook.tasks.service-url}") String serviceUrl) {
        this.cloudTasksClient = cloudTasksClient;
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.location = location;
        this.queueName = queueName;
        this.serviceUrl = serviceUrl;
    }

    public void enqueueYouTubeJob(String jobId, String userId, String videoUrl) {
        log.info("Enqueuing YouTube job: {} for user: {}, video: {}", jobId, userId, videoUrl);

        try {
            Map<String, String> payload = Map.of(
                    "jobId", jobId,
                    "userId", userId,
                    "videoUrl", videoUrl
            );
            String jsonPayload = objectMapper.writeValueAsString(payload);

            String targetUrl = serviceUrl + "/internal/youtube-tasks/process";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .setUrl(targetUrl)
                    .setHttpMethod(HttpMethod.POST)
                    .putHeaders("Content-Type", "application/json")
                    .setBody(ByteString.copyFromUtf8(jsonPayload))
                    .build();

            Task task = Task.newBuilder()
                    .setHttpRequest(httpRequest)
                    .build();

            QueueName parent = QueueName.of(projectId, location, queueName);
            Task createdTask = cloudTasksClient.createTask(parent, task);

            log.info("Created Cloud Task: {} for YouTube job: {}", createdTask.getName(), jobId);
        } catch (Exception e) {
            log.error("Failed to enqueue YouTube job: {}", jobId, e);
            throw new RuntimeException("Failed to enqueue YouTube processing job", e);
        }
    }
}
