package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
@Slf4j
public class TokenRestService implements TokenService {

    private final WebClient authClient;

    public TokenRestService(@Value("${cookbook.drive.auth-url}") String authUrl) {
        this.authClient = WebClient.builder().baseUrl(authUrl).build();
    }

    /**
     * Verifies the provided OAuth2 access token by calling Google's tokeninfo endpoint.
     *
     * @param authToken OAuth2 access token header value
     * @return true if token is valid
     */
    @Override
    public boolean verifyToken(String authToken) {
        try {
            Map<?, ?> tokenInfo = authClient.get().uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("access_token", authToken).build()).retrieve().bodyToMono(Map.class).block();
            return tokenInfo != null && tokenInfo.containsKey("aud");
        } catch (WebClientResponseException e) {
            log.warn("Token verification failed: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error verifying token", e);
            return false;
        }
    }
}
