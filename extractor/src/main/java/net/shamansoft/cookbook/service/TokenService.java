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
     * Retrieves the Google OAuth access token for Drive API from HTTP headers.
     * <p>
     * IMPORTANT - Dual-Token Model:
     * - Firebase ID token: Used for backend authentication (in Authorization header)
     * → Validated by FirebaseAuthFilter
     * - Google OAuth token: Used for Google Drive API access (in X-Google-Token header)
     * → Retrieved by this method
     * <p>
     * Clients must send both tokens:
     * - Authorization: Bearer <firebase-id-token>
     * - X-Google-Token: <google-oauth-token>
     *
     * @param httpHeaders HTTP headers containing the Google OAuth token
     * @return the Google OAuth access token if valid
     * @throws AuthenticationException if the token is invalid or not present
     */
    String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException;
}
