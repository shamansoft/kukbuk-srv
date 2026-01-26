package net.shamansoft.cookbook.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.assertj.core.api.Assertions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification test for Spring Boot 4 test dependencies.
 * This test ensures that JUnit 5 (Jupiter), Mockito, and AssertJ are available and functional.
 */
class TestDependenciesTest {

    @Test
    void junitJupiterIsAvailable() {
        // If this test runs, JUnit Jupiter is working
        assertThat(true).isTrue();
    }

    @Test
    void mockitoIsAvailable() {
        // Verify Mockito can create mocks
        Runnable mockRunnable = Mockito.mock(Runnable.class);
        assertThat(mockRunnable).isNotNull();
    }

    @Test
    void assertjIsAvailable() {
        // Verify AssertJ assertions work
        assertThat("test")
                .isNotNull()
                .isEqualTo("test")
                .startsWith("t")
                .endsWith("t");
    }

    @Test
    void junitVersionIsAtLeast6() {
        // JUnit 6 (Jupiter 6.x) is required for Spring Boot 4
        String junitVersion = org.junit.jupiter.api.Assertions.class.getPackage().getImplementationVersion();
        // Version format: 6.0.1 or similar
        if (junitVersion != null) {
            assertThat(junitVersion).matches("^6\\..*");
        }
        // If version is null (happens in some IDE test runners), just verify the class exists
        assertThat(org.junit.jupiter.api.Assertions.class).isNotNull();
    }
}
