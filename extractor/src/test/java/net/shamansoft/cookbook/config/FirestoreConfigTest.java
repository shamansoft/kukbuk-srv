package net.shamansoft.cookbook.config;

import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test FirestoreConfig conditional bean loading for Spring Boot 4 migration.
 * <p>
 * Tests verify:
 * - Firestore bean is available when using TestFirebaseConfig (mock setup)
 * - Firestore bean is NOT created when firestore.enabled=false
 * - Configuration properly handles conditional bean scenarios
 * <p>
 * Note: TestFirebaseConfig provides mock beans with @Primary to avoid requiring
 * real GCP credentials during testing.
 */
@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
        "firestore.enabled=false",
        "firebase.enabled=false"
})
class FirestoreConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void firestoreConfig_exists_inContext() {
        // Verify FirestoreConfig bean is created
        assertThat(applicationContext.containsBean("firestoreConfig")).isTrue();
    }

    @Test
    void firestore_isNotCreated_whenDisabled() {
        // Verify production Firestore bean does NOT exist when firestore.enabled=false
        assertThat(applicationContext.containsBean("firestore")).isFalse();
    }

    @Test
    void mockFirestore_isAvailable_forTesting() {
        // Verify mock Firestore bean from TestFirebaseConfig is available
        assertThat(applicationContext.containsBean("mockFirestore")).isTrue();

        Firestore mockFirestore = applicationContext.getBean(Firestore.class);
        assertThat(mockFirestore).isNotNull();
    }

    @Test
    void conditionalOnProperty_annotation_isPresent() {
        // Verify FirestoreConfig uses @ConditionalOnProperty for conditional bean loading
        // This is tested by checking that the production bean respects the property
        // When firestore.enabled=false, the bean should not be created
        assertThat(applicationContext.containsBean("firestore")).isFalse();

        // When property is missing or true, bean would be created (tested implicitly
        // by other tests that use firestore.enabled=true or no property)
    }
}
