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
 * Client for handling Google OAuth2 authentication operations.
 * Responsible for exchanging authorization codes, refreshing tokens, and token validation.
 */
@Service
@Slf4j
public class GoogleAuthClient {

    // Refresh token if it expires within this window (5 minutes)
    private static final long TOKEN_BUFFER_SECONDS = 300;
    private final RestClient restClient;
    @Value("${cookbook.drive.oauth-id}")
    private String googleClientId;
    @Value("${cookbook.drive.oauth-secret}")
    private String googleClientSecret;

    public GoogleAuthClient(@Qualifier("genericRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Exchange authorization code for OAuth tokens.
     * Calls Google's OAuth token endpoint with the authorization code.
     *
     * @param authorizationCode Authorization code from OAuth flow
     * @param redirectUri       Redirect URI that was used in the OAuth flow
     * @return TokenResponse with access token, refresh token, and expiration
     * @throws IllegalArgumentException     if authorization code is invalid
     * @throws DatabaseUnavailableException if OAuth service is unavailable
     */
    public TokenResponse exchangeAuthorizationCode(String authorizationCode, String redirectUri) {
        log.info("Exchanging authorization code for OAuth tokens");
        log.info("OAuth exchange - client_id: {}, redirect_uri: {}", googleClientId, redirectUri);

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authorizationCode);
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("access_type", "offline");
            params.add("grant_type", "authorization_code");
            params.add("prompt", "consent");

            Map<String, Object> response = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalArgumentException("Invalid response from Google OAuth: " + response);
            }

            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            if (refreshToken == null) {
                throw new IllegalStateException(
                        "No refresh token received. Ensure OAuth consent screen requests offline access."
                );
            }

            if (expiresIn == null) {
                expiresIn = 3600; // Default to 1 hour
            }

            log.info("Successfully exchanged authorization code for tokens, expires in {}s", expiresIn);

            return new TokenResponse(accessToken, refreshToken, expiresIn.longValue());

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("OAuth token exchange failed with status {}: {}", e.getStatusCode(), errorBody);

            // Try to extract error from Google's JSON response
            String errorMessage = "Invalid authorization code";
            try {
                if (errorBody.contains("error")) {
                    // Google returns {"error": "invalid_grant", "error_description": "..."}
                    errorMessage = errorBody;
                }
            } catch (Exception parseEx) {
                // Ignore parsing errors, use default message
            }

            throw new IllegalArgumentException(
                    "OAuth token exchange failed: " + errorMessage +
                            ". Check that client_id, client_secret, and redirect_uri match your OAuth configuration."
            );
        } catch (Exception e) {
            log.error("Failed to exchange authorization code: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("OAuth token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * Refresh access token using refresh token.
     * Calls Google's token endpoint to get a new access token.
     *
     * @param refreshToken OAuth refresh token
     * @return RefreshTokenResponse with new access token and expiration time
     * @throws DatabaseUnavailableException if OAuth service is unavailable
     */
    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        log.info("Calling Google OAuth token endpoint to refresh access token");

        try {
            // Call Google's token endpoint
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("refresh_token", refreshToken);
            params.add("grant_type", "refresh_token");

            Map<String, Object> response = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Invalid response from Google OAuth: " + response);
            }

            String newAccessToken = (String) response.get("access_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            if (expiresIn == null) {
                expiresIn = 3600; // Default to 1 hour
            }

            log.info("Successfully refreshed OAuth token, expires in {}s", expiresIn);

            // Calculate new expiration timestamp
            Timestamp newExpiresAt = Timestamp.ofTimeSecondsAndNanos(
                    System.currentTimeMillis() / 1000 + expiresIn, 0
            );

            return new RefreshTokenResponse(newAccessToken, newExpiresAt);

        } catch (Exception e) {
            log.error("Failed to refresh OAuth token: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to refresh OAuth token: " + e.getMessage(), e);
        }
    }

    /**
     * Check if access token is expired or expiring soon.
     * Uses a buffer period to refresh tokens before they actually expire.
     *
     * @param expiresAt Token expiration timestamp
     * @return true if token is expired or expiring within buffer period
     */
    public boolean isTokenExpired(Timestamp expiresAt) {
        if (expiresAt == null) {
            log.warn("No expiration time found for token, assuming expired");
            return true;
        }

        long now = System.currentTimeMillis() / 1000;
        long expiresAtSeconds = expiresAt.getSeconds();

        boolean isExpired = (expiresAtSeconds - now) <= TOKEN_BUFFER_SECONDS;

        if (isExpired) {
            log.debug("Token expires at {}, now is {}, buffer is {}s - needs refresh",
                    expiresAtSeconds, now, TOKEN_BUFFER_SECONDS);
        }

        return isExpired;
    }

    /**
     * Response from exchanging authorization code for tokens.
     *
     * @param accessToken  OAuth access token
     * @param refreshToken OAuth refresh token
     * @param expiresIn    Token expiration in seconds
     */
    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    /**
     * Response from refreshing access token.
     *
     * @param accessToken New access token
     * @param expiresAt   New expiration timestamp
     */
    public record RefreshTokenResponse(String accessToken, Timestamp expiresAt) {
    }
}
