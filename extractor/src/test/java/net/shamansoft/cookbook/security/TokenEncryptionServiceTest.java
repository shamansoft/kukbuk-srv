package net.shamansoft.cookbook.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TokenEncryptionService.
 *
 * Tests focus on @PostConstruct initialization behavior.
 * Full encryption/decryption functionality is tested via integration tests
 * since it requires GCP KMS credentials.
 */
@ExtendWith(MockitoExtension.class)
class TokenEncryptionServiceTest {

    @Test
    void postConstructSkipsKmsInitializationWhenDisabled() {
        // Given
        TokenEncryptionService service = new TokenEncryptionService();
        ReflectionTestUtils.setField(service, "kmsEnabled", false);
        ReflectionTestUtils.setField(service, "projectId", "test-project");
        ReflectionTestUtils.setField(service, "location", "us-west1");
        ReflectionTestUtils.setField(service, "keyring", "test-keyring");
        ReflectionTestUtils.setField(service, "keyName", "test-key");

        // When
        service.initKmsClient();

        // Then
        Object kmsClient = ReflectionTestUtils.getField(service, "kmsClient");
        assertThat(kmsClient).isNull();
    }

    @Test
    void kmsClientIsNullWhenKmsDisabled() {
        // Given
        TokenEncryptionService service = new TokenEncryptionService();
        ReflectionTestUtils.setField(service, "kmsEnabled", false);

        // When
        service.initKmsClient();

        // Then
        Object kmsClient = ReflectionTestUtils.getField(service, "kmsClient");
        assertThat(kmsClient).as("KMS client should remain null when disabled").isNull();
    }
}
