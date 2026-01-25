package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.naming.AuthenticationException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenRestService implements TokenService {

    private final RestClient authRestClient;

    @Override
    public boolean verifyToken(String authToken) {
        if (authToken == null || authToken.isEmpty()) {
            log.warn("Token is null or empty");
            return false;
        }
        try {
            Map<?, ?> tokenInfo = authRestClient.get().uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("access_token", authToken).build()).retrieve().body(Map.class);
            return tokenInfo != null && tokenInfo.containsKey("aud");
        } catch (RestClientResponseException e) {
            log.warn("Token verification failed: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error verifying token", e);
            return false;
        }
    }

    @Override
    public String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException {
        // Look for Google OAuth token in X-Google-Token header
        String authToken = httpHeaders.getFirst("X-Google-Token");

        if (authToken == null || authToken.isBlank()) {
            log.warn("No Google OAuth token found in X-Google-Token header");
            throw new AuthenticationException("Google OAuth token required for Drive access");
        }

        // Optionally verify the token is valid
        if (!verifyToken(authToken)) {
            throw new AuthenticationException("Invalid Google OAuth token");
        }

        return authToken;
    }
}
