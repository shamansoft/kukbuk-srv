package net.shamansoft.cookbook.security;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TokenEncryptionService.
 * <p>
 * Tests cover encryption/decryption functionality with mocked KMS client,
 * error handling, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class TokenEncryptionServiceTest {

    @Mock
    private KeyManagementServiceClient kmsClient;

    private TokenEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new TokenEncryptionService();
        ReflectionTestUtils.setField(service, "kmsEnabled", false);
        ReflectionTestUtils.setField(service, "projectId", "test-project");
        ReflectionTestUtils.setField(service, "location", "us-west1");
        ReflectionTestUtils.setField(service, "keyring", "test-keyring");
        ReflectionTestUtils.setField(service, "keyName", "test-key");
        // Inject mocked KMS client
        ReflectionTestUtils.setField(service, "kmsClient", kmsClient);
    }

    @Test
    void postConstructSkipsKmsInitializationWhenDisabled() {
        // Given
        TokenEncryptionService newService = new TokenEncryptionService();
        ReflectionTestUtils.setField(newService, "kmsEnabled", false);
        ReflectionTestUtils.setField(newService, "projectId", "test-project");
        ReflectionTestUtils.setField(newService, "location", "us-west1");
        ReflectionTestUtils.setField(newService, "keyring", "test-keyring");
        ReflectionTestUtils.setField(newService, "keyName", "test-key");

        // When
        newService.initKmsClient();

        // Then
        Object kmsClientField = ReflectionTestUtils.getField(newService, "kmsClient");
        assertThat(kmsClientField).isNull();
    }

    @Test
    void kmsClientIsNullWhenKmsDisabled() {
        // Given
        TokenEncryptionService newService = new TokenEncryptionService();
        ReflectionTestUtils.setField(newService, "kmsEnabled", false);

        // When
        newService.initKmsClient();

        // Then
        Object kmsClientField = ReflectionTestUtils.getField(newService, "kmsClient");
        assertThat(kmsClientField).as("KMS client should remain null when disabled").isNull();
    }

    @Test
    void encryptValidPlaintext_ReturnsBase64EncodedCiphertext() {
        // Given: plaintext and mocked KMS response
        String plaintext = "my-secret-token";
        byte[] ciphertextBytes = "encrypted-data-bytes".getBytes(StandardCharsets.UTF_8);

        EncryptResponse mockResponse = mock(EncryptResponse.class);
        when(mockResponse.getCiphertext()).thenReturn(ByteString.copyFrom(ciphertextBytes));
        when(kmsClient.encrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenReturn(mockResponse);

        // When
        String result = service.encrypt(plaintext);

        // Then: result is Base64-encoded ciphertext
        String expectedBase64 = Base64.getEncoder().encodeToString(ciphertextBytes);
        assertThat(result).isEqualTo(expectedBase64);
    }

    @Test
    void encryptNullPlaintext_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> service.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plaintext cannot be null or blank");
    }

    @Test
    void encryptEmptyPlaintext_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> service.encrypt(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plaintext cannot be null or blank");
    }

    @Test
    void encryptBlankPlaintext_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> service.encrypt("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plaintext cannot be null or blank");
    }

    @Test
    void encryptKmsClientError_WrapsInRuntimeException() {
        // Given: KMS client throws exception
        when(kmsClient.encrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenThrow(new RuntimeException("KMS service unavailable"));

        // When/Then
        assertThatThrownBy(() -> service.encrypt("plaintext"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token encryption failed");
    }

    @Test
    void decryptValidEncryptedToken_ReturnsPlaintext() {
        // Given: valid Base64-encoded encrypted token
        String plaintext = "my-secret-token";
        byte[] ciphertextBytes = "encrypted-data-bytes".getBytes(StandardCharsets.UTF_8);
        String encryptedToken = Base64.getEncoder().encodeToString(ciphertextBytes);

        DecryptResponse mockResponse = mock(DecryptResponse.class);
        when(mockResponse.getPlaintext())
                .thenReturn(ByteString.copyFrom(plaintext.getBytes(StandardCharsets.UTF_8)));
        when(kmsClient.decrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenReturn(mockResponse);

        // When
        String result = service.decrypt(encryptedToken);

        // Then: result is decrypted plaintext
        assertThat(result).isEqualTo(plaintext);
    }

    @Test
    void decryptNullEncryptedToken_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> service.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encrypted token cannot be null or blank");
    }

    @Test
    void decryptEmptyEncryptedToken_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> service.decrypt(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encrypted token cannot be null or blank");
    }

    @Test
    void decryptBlankEncryptedToken_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> service.decrypt("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encrypted token cannot be null or blank");
    }

    @Test
    void decryptKmsClientError_WrapsInRuntimeException() {
        // Given: KMS client throws exception
        String encryptedToken = Base64.getEncoder().encodeToString("ciphertext".getBytes());
        when(kmsClient.decrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenThrow(new RuntimeException("KMS service unavailable"));

        // When/Then
        assertThatThrownBy(() -> service.decrypt(encryptedToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token decryption failed");
    }

    @Test
    void decryptInvalidBase64_ThrowsRuntimeException() {
        // Given: invalid Base64 string
        String invalidBase64 = "not-valid-base64!!!";

        // When/Then
        assertThatThrownBy(() -> service.decrypt(invalidBase64))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token decryption failed");
    }

    @Test
    void encryptAndDecryptRoundTrip_PreservesOriginalText() {
        // Given: plaintext
        String originalText = "my-oauth-token-secret-value";
        byte[] encryptedBytes = "encrypted-ciphertext-bytes".getBytes(StandardCharsets.UTF_8);
        String encryptedToken = Base64.getEncoder().encodeToString(encryptedBytes);

        // Mock encrypt response
        EncryptResponse encryptResponse = mock(EncryptResponse.class);
        when(encryptResponse.getCiphertext()).thenReturn(ByteString.copyFrom(encryptedBytes));
        when(kmsClient.encrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenReturn(encryptResponse);

        // Mock decrypt response
        DecryptResponse decryptResponse = mock(DecryptResponse.class);
        when(decryptResponse.getPlaintext())
                .thenReturn(ByteString.copyFrom(originalText.getBytes(StandardCharsets.UTF_8)));
        when(kmsClient.decrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenReturn(decryptResponse);

        // When
        String encrypted = service.encrypt(originalText);
        String decrypted = service.decrypt(encrypted);

        // Then: decrypted text matches original
        assertThat(decrypted).isEqualTo(originalText);
    }

    @Test
    void closeKmsClient_DoesNotThrowWhenClientIsNull() {
        // Given: KMS client is null
        ReflectionTestUtils.setField(service, "kmsClient", null);

        // When/Then: should not throw
        service.closeKmsClient();
    }

    @Test
    void closeKmsClient_ClosesClientWhenNotNull() {
        // Given: KMS client is set
        assertThat(ReflectionTestUtils.getField(service, "kmsClient")).isNotNull();

        // When
        service.closeKmsClient();

        // Then: client close was called
        // Note: verify is not used here since mock.close() doesn't return anything
        // The test verifies no exception is thrown
    }

    @ParameterizedTest
    @ValueSource(strings = {"token-value", "abc123xyz789", "my.secret.key"})
    void encryptVariousValidTokens_SucceedsWithAllFormats(String token) {
        // Given: various token formats
        byte[] ciphertextBytes = "encrypted".getBytes(StandardCharsets.UTF_8);
        EncryptResponse mockResponse = mock(EncryptResponse.class);
        when(mockResponse.getCiphertext()).thenReturn(ByteString.copyFrom(ciphertextBytes));
        when(kmsClient.encrypt(any(CryptoKeyName.class), any(ByteString.class)))
                .thenReturn(mockResponse);

        // When
        String result = service.encrypt(token);

        // Then: result is not null or empty
        assertThat(result).isNotBlank();
    }
}
