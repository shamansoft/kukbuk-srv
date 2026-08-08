package net.shamansoft.cookbook.client;

import com.google.cloud.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Client for Dropbox OAuth2 (authorization-code + offline refresh token).
 * Mirrors {@link GoogleAuthClient}. Non-PKCE: the app secret is held server-side.
 */
@Service
@Slf4j
public class DropboxAuthClient {

    private static final long TOKEN_BUFFER_SECONDS = 300;
    private static final String TOKEN_URL = "https://api.dropboxapi.com/oauth2/token";
    private static final String REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke";
    private static final int DEFAULT_EXPIRES_IN = 14400;

    private final RestClient restClient;

    @Value("${cookbook.dropbox.app-key:REPLACE_WITH_DROPBOX_APP_KEY}")
    private String appKey;
    @Value("${cookbook.dropbox.app-secret:REPLACE_WITH_DROPBOX_APP_SECRET}")
    private String appSecret;

    public DropboxAuthClient(@Qualifier("genericRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TokenResponse exchangeAuthorizationCode(String authorizationCode, String redirectUri) {
        log.info("Exchanging Dropbox authorization code for tokens, redirect_uri: {}", redirectUri);
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authorizationCode);
            params.add("grant_type", "authorization_code");
            params.add("client_id", appKey);
            params.add("client_secret", appSecret);
            params.add("redirect_uri", redirectUri);

            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalArgumentException("Invalid response from Dropbox OAuth: " + response);
            }
            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");
            if (refreshToken == null) {
                throw new IllegalStateException(
                        "No refresh token received. Ensure token_access_type=offline in the authorize URL.");
            }
            if (expiresIn == null) {
                expiresIn = DEFAULT_EXPIRES_IN;
            }
            log.info("Exchanged Dropbox authorization code, expires in {}s", expiresIn);
            return new TokenResponse(accessToken, refreshToken, expiresIn.longValue());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("Dropbox token exchange failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalArgumentException(
                    "Dropbox OAuth token exchange failed: " + e.getResponseBodyAsString()
                            + ". Check that app-key, app-secret, and redirect_uri match your Dropbox app configuration.");
        } catch (Exception e) {
            log.error("Failed to exchange Dropbox authorization code: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("Dropbox OAuth token exchange failed: " + e.getMessage(), e);
        }
    }

    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        log.info("Refreshing Dropbox access token");
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("refresh_token", refreshToken);
            params.add("client_id", appKey);
            params.add("client_secret", appSecret);

            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Invalid response from Dropbox OAuth: " + response);
            }
            String newAccessToken = (String) response.get("access_token");
            Integer expiresIn = (Integer) response.get("expires_in");
            if (expiresIn == null) {
                expiresIn = DEFAULT_EXPIRES_IN;
            }
            Timestamp newExpiresAt = Timestamp.ofTimeSecondsAndNanos(
                    System.currentTimeMillis() / 1000 + expiresIn, 0);
            log.info("Refreshed Dropbox token, expires in {}s", expiresIn);
            return new RefreshTokenResponse(newAccessToken, newExpiresAt);
        } catch (Exception e) {
            log.error("Failed to refresh Dropbox token: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to refresh Dropbox token: " + e.getMessage(), e);
        }
    }

    public boolean isTokenExpired(Timestamp expiresAt) {
        if (expiresAt == null) {
            return true;
        }
        long now = System.currentTimeMillis() / 1000;
        return (expiresAt.getSeconds() - now) <= TOKEN_BUFFER_SECONDS;
    }

    /**
     * Best-effort server-side token revocation. Never throws.
     */
    public void revokeToken(String accessToken) {
        try {
            restClient.post()
                    .uri(REVOKE_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Revoked Dropbox token");
        } catch (Exception e) {
            log.warn("Dropbox token revoke failed (ignored): {}", e.getMessage());
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    public record RefreshTokenResponse(String accessToken, Timestamp expiresAt) {
    }
}
