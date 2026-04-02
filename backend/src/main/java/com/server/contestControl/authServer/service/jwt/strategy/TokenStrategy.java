package com.server.contestControl.authServer.service.jwt.strategy;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public interface TokenStrategy {
    String generateToken(UserDetails userDetails , Long id);

    Claims extractAllClaims(String token);

    boolean isTokenValid(String token, UserDetails userDetails);

    String getTokenType();

    default String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    long getTokenExpiration();
}
