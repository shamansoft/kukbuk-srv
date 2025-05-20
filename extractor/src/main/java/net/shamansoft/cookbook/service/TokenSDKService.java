package net.shamansoft.cookbook.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Slf4j
@RequiredArgsConstructor
public class TokenSDKService implements TokenService {

    final private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Override
    public boolean verifyToken(String authToken) {
        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(authToken);
            return idToken != null;
        } catch (Exception e) {
            log.error("Error verifying token", e);
            return false;
        }
    }
}
