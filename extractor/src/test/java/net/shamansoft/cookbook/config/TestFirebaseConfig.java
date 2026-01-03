package net.shamansoft.cookbook.config;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration that provides mock Firebase/Firestore/KMS beans for unit tests.
 *
 * This replaces the production beans during tests, allowing tests to run
 * without needing real GCP credentials.
 */
@TestConfiguration
public class TestFirebaseConfig {

    @Bean
    @Primary
    public FirebaseApp mockFirebaseApp() {
        return mock(FirebaseApp.class);
    }

    @Bean
    @Primary
    public FirebaseAuth mockFirebaseAuth() throws Exception {
        FirebaseAuth mockAuth = mock(FirebaseAuth.class);

        // Create a mock FirebaseToken that will be returned for any token verification
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("test-user-id");
        when(mockToken.getEmail()).thenReturn("test@example.com");

        // Mock verifyIdToken to return the mock token for any input
        when(mockAuth.verifyIdToken(anyString())).thenReturn(mockToken);

        return mockAuth;
    }

    @Bean
    @Primary
    public Firestore mockFirestore() {
        return mock(Firestore.class);
    }

    /**
     * Mock TokenEncryptionService that doesn't require GCP KMS credentials.
     * Uses simple string prefixing to simulate encryption/decryption.
     */
    @Bean
    @Primary
    public TokenEncryptionService mockTokenEncryptionService() throws Exception {
        TokenEncryptionService mockService = mock(TokenEncryptionService.class);

        // Mock encrypt to add "encrypted-" prefix
        when(mockService.encrypt(anyString())).thenAnswer(invocation -> {
            String plaintext = invocation.getArgument(0);
            return "encrypted-" + plaintext;
        });

        // Mock decrypt to remove "encrypted-" prefix
        when(mockService.decrypt(anyString())).thenAnswer(invocation -> {
            String ciphertext = invocation.getArgument(0);
            if (ciphertext != null && ciphertext.startsWith("encrypted-")) {
                return ciphertext.substring("encrypted-".length());
            }
            return ciphertext;
        });

        return mockService;
    }
}
