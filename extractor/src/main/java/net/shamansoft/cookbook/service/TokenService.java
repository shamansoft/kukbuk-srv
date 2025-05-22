package net.shamansoft.cookbook.service;

import org.springframework.http.HttpHeaders;

import javax.naming.AuthenticationException;

public interface TokenService {

    /**
     * Verifies the provided OAuth2 access token by calling Google's tokeninfo endpoint.
     *
     * @param authToken OAuth2 access token header value
     * @return true if token is valid
     */
    boolean verifyToken(String authToken);

    /**
     * Retrieves the auth token from the HTTP headers and verifies it.
     *
     * @param httpHeaders HTTP headers containing the auth token
     * @return the auth token if valid
     * @throws AuthenticationException if the token is invalid or not present
     */
    String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException;
}
