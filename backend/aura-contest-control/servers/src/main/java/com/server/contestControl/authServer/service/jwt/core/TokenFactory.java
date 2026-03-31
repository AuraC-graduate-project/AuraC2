package com.server.contestControl.authServer.service.jwt.core;

import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.service.jwt.strategy.AccessTokenStrategy;
import com.server.contestControl.authServer.service.jwt.strategy.RefreshTokenStrategy;
import com.server.contestControl.authServer.service.jwt.strategy.TokenStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenFactory {

    private final AccessTokenStrategy accessTokenStrategy;
    private final RefreshTokenStrategy refreshTokenStrategy;

    public TokenStrategy getStrategy(TokenType type) {
        return switch (type) {
            case ACCESS -> accessTokenStrategy;
            case REFRESH -> refreshTokenStrategy;
        };
    }
}