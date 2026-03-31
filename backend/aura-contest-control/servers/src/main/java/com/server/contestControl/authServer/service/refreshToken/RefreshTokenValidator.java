package com.server.contestControl.authServer.service.refreshToken;

import com.server.contestControl.authServer.dto.refresh.TokenValidationResult;
import com.server.contestControl.authServer.entity.RefreshToken;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.exception.api.*;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.authServer.service.user.UserService;
import com.server.contestControl.authServer.util.TokenHashUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenValidator {

    private final JwtService jwtService;
    private final UserService userService;
    private final RefreshTokenRepoService refreshTokenRepoService;

    public TokenValidationResult validate(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new MissingTokenException();
        }

        Claims claims = jwtService.extractAllClaims(rawToken, TokenType.REFRESH);

        String username = claims.getSubject();
        Long refreshTokenId = Long.parseLong(claims.getId());

        User user = userService.getUserByUsernameOrThrow(username);

        if (!jwtService.isTokenValid(rawToken, user, TokenType.REFRESH)) {
            throw new TokenExpiredException();
        }

        RefreshToken storedToken = refreshTokenRepoService.findById(refreshTokenId)
                .orElseThrow(RefreshTokenRevokedException::new);

        if (storedToken.isRevoked()) {
            throw new RefreshTokenRevokedException();
        }

        String hashedRawToken = TokenHashUtil.hash(rawToken);
        if (!hashedRawToken.equals(storedToken.getTokenHash())) {
            throw new InvalidTokenException("Token hash mismatch");
        }

        if (!storedToken.getUser().getId().equals(user.getId())) {
            throw new TokenOwnershipException();
        }

        return new TokenValidationResult(user, storedToken, refreshTokenId);
    }
}
