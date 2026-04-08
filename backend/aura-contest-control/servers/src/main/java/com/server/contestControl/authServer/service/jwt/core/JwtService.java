package com.server.contestControl.authServer.service.jwt.core;

import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.service.jwt.strategy.TokenStrategy;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final TokenFactory tokenFactory;

    public String generateToken(UserDetails userDetails, TokenType type , Long id) {
        TokenStrategy strategy = tokenFactory.getStrategy(type);
        return strategy.generateToken(userDetails , id);
    }



    public boolean isTokenValid(String token, UserDetails userDetails, TokenType type) {
        return tokenFactory.getStrategy(type).isTokenValid(token, userDetails);
    }

    public Claims extractAllClaims(String token, TokenType type) {
        return tokenFactory.getStrategy(type).extractAllClaims(token);
    }

    public String extractUsername(String token, TokenType type) {
        return tokenFactory.getStrategy(type).extractUsername(token);
    }

    public Long getExpiration(TokenType type) {
        return tokenFactory.getStrategy(type).getTokenExpiration();
    }
}