package net.shamansoft.cookbook.service;

import org.springframework.http.HttpHeaders;

import javax.naming.AuthenticationException;

public interface TokenService {

    boolean verifyToken(String authToken);
    String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException;
}
