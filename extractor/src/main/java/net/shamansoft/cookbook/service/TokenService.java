package net.shamansoft.cookbook.service;

public interface TokenService {

    boolean verifyToken(String authToken);
}
