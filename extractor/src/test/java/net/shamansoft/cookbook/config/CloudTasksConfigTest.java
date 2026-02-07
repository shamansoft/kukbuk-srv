package net.shamansoft.cookbook.config;

import com.google.cloud.tasks.v2.CloudTasksClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
        "firestore.enabled=false",
        "firebase.enabled=false",
        "cookbook.tasks.enabled=false"
})
class CloudTasksConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void cloudTasksConfig_exists_inContext() {
        assertThat(applicationContext.containsBean("cloudTasksConfig")).isTrue();
    }

    @Test
    void cloudTasksClient_isNotCreated_whenDisabled() {
        assertThat(applicationContext.containsBean("cloudTasksClient")).isFalse();
    }

    @Test
    void cloudTasksClient_beanType_isCorrect() {
        // Verify that when disabled, no CloudTasksClient bean exists
        assertThat(applicationContext.getBeanNamesForType(CloudTasksClient.class)).isEmpty();
    }
}
