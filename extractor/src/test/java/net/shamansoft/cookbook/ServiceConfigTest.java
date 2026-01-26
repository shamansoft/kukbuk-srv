package net.shamansoft.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

import net.shamansoft.cookbook.config.TestFirebaseConfig;

/**
 * Test ServiceConfig Actuator bean configuration for Spring Boot 4 migration.
 *
 * Tests verify:
 * - HttpExchangeRepository bean is created for Actuator httpexchanges endpoint
 * - Bean is configured as InMemoryHttpExchangeRepository
 * - Bean is available in the application context
 */
@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
        "firestore.enabled=false",
        "firebase.enabled=false"
})
class ServiceConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ServiceConfig serviceConfig;

    @Autowired
    private HttpExchangeRepository httpExchangeRepository;

    @Test
    void serviceConfig_isCreated_asBean() {
        // Verify ServiceConfig bean is created
        assertThat(serviceConfig).isNotNull();
    }

    @Test
    void httpExchangeRepository_isCreated_asBean() {
        // Verify HttpExchangeRepository bean exists in the context
        assertThat(applicationContext.containsBean("httpExchangeRepository")).isTrue();
    }

    @Test
    void httpExchangeRepository_isInstanceOf_inMemoryImplementation() {
        // Verify the bean is an InMemoryHttpExchangeRepository
        assertThat(httpExchangeRepository).isNotNull();
        assertThat(httpExchangeRepository).isInstanceOf(InMemoryHttpExchangeRepository.class);
    }

    @Test
    void httpExchangeRepository_canBeAutowired() {
        // Verify the repository can be autowired successfully
        assertThat(httpExchangeRepository).isNotNull();

        // InMemoryHttpExchangeRepository should be ready to store exchanges
        // (actual exchange recording is tested in integration tests)
    }

    @Test
    void httpExchangeRepository_isReady_forActuatorEndpoint() {
        // Verify the bean is properly configured for Actuator httpexchanges endpoint
        // This bean enables the /actuator/httpexchanges endpoint
        assertThat(httpExchangeRepository).isNotNull();
        assertThat(httpExchangeRepository).isInstanceOf(InMemoryHttpExchangeRepository.class);

        // The repository should be able to record HTTP exchanges
        // (Spring Boot Actuator will use this bean automatically)
    }
}
