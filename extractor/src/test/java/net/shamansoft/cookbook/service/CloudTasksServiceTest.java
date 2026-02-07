package net.shamansoft.cookbook.service;

import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudTasksServiceTest {

    private static final String PROJECT_ID = "test-project";
    private static final String LOCATION = "us-west1";
    private static final String QUEUE_NAME = "youtube-recipes";
    private static final String SERVICE_URL = "https://cookbook.run.app";

    @Mock
    private CloudTasksClient cloudTasksClient;

    private ObjectMapper objectMapper;
    private CloudTasksService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        service = new CloudTasksService(
                cloudTasksClient, objectMapper,
                PROJECT_ID, LOCATION, QUEUE_NAME, SERVICE_URL);
    }

    @Test
    @DisplayName("Should create Cloud Task with correct queue and payload")
    void shouldCreateTaskWithCorrectPayload() {
        String jobId = "job-123";
        String userId = "user-456";
        String videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        Task createdTask = Task.newBuilder().setName("tasks/test-task").build();
        when(cloudTasksClient.createTask(any(QueueName.class), any(Task.class)))
                .thenReturn(createdTask);

        service.enqueueYouTubeJob(jobId, userId, videoUrl);

        ArgumentCaptor<QueueName> queueCaptor = ArgumentCaptor.forClass(QueueName.class);
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(cloudTasksClient).createTask(queueCaptor.capture(), taskCaptor.capture());

        QueueName capturedQueue = queueCaptor.getValue();
        assertThat(capturedQueue.getProject()).isEqualTo(PROJECT_ID);
        assertThat(capturedQueue.getLocation()).isEqualTo(LOCATION);
        assertThat(capturedQueue.getQueue()).isEqualTo(QUEUE_NAME);

        Task capturedTask = taskCaptor.getValue();
        assertThat(capturedTask.getHttpRequest().getUrl())
                .isEqualTo(SERVICE_URL + "/internal/youtube-tasks/process");
        assertThat(capturedTask.getHttpRequest().getHttpMethod())
                .isEqualTo(com.google.cloud.tasks.v2.HttpMethod.POST);
        assertThat(capturedTask.getHttpRequest().getHeadersMap())
                .containsEntry("Content-Type", "application/json");

        String body = capturedTask.getHttpRequest().getBody().toStringUtf8();
        assertThat(body).contains("\"jobId\"");
        assertThat(body).contains("job-123");
        assertThat(body).contains("\"userId\"");
        assertThat(body).contains("user-456");
        assertThat(body).contains("\"videoUrl\"");
        assertThat(body).contains(videoUrl);
    }

    @Test
    @DisplayName("Should throw RuntimeException when Cloud Tasks client fails")
    void shouldThrowWhenClientFails() {
        when(cloudTasksClient.createTask(any(QueueName.class), any(Task.class)))
                .thenThrow(new RuntimeException("Cloud Tasks unavailable"));

        assertThatThrownBy(() -> service.enqueueYouTubeJob("job-1", "user-1", "https://youtu.be/abc12345678"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to enqueue YouTube processing job");
    }
}
