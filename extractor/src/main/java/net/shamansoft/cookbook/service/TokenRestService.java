package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.naming.AuthenticationException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenRestService implements TokenService {

    private final WebClient authWebClient;

    @Override
    public boolean verifyToken(String authToken) {
        if (authToken == null || authToken.isEmpty()) {
            log.warn("Token is null or empty");
            return false;
        }
        try {
            Map<?, ?> tokenInfo = authWebClient.get().uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("access_token", authToken).build()).retrieve().bodyToMono(Map.class).block();
            return tokenInfo != null && tokenInfo.containsKey("aud");
        } catch (WebClientResponseException e) {
            log.warn("Token verification failed: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error verifying token", e);
            return false;
        }
    }

    @Override
    public String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException {
        String authToken = httpHeaders.getFirst("X-S-AUTH-TOKEN");
        if (authToken == null || authToken.isBlank() || !verifyToken(authToken)) {
            throw new AuthenticationException("Invalid auth token");
        }
        return authToken;
    }
}
