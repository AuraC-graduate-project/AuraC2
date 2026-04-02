package com.server.contestControl.authServer.util;

import com.server.contestControl.authServer.config.CookieConfig;
import com.server.contestControl.authServer.entity.RefreshToken;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.authServer.service.refreshToken.RefreshTokenRepoService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenIssuerUtil {

    private final JwtService jwtService;
    private final RefreshTokenRepoService refreshTokenRepoService;
    private final PasswordEncoder passwordEncoder;
    private final CookieConfig cookieConfig;


    public String issueTokens(User user, String deviceIp, HttpServletResponse response) {
        RefreshToken refreshEntity = refreshTokenRepoService.createAndSave(user, deviceIp);

        String accessToken  = jwtService.generateToken(user, TokenType.ACCESS, null);
        String refreshToken = jwtService.generateToken(user, TokenType.REFRESH, refreshEntity.getId());

        refreshEntity.setTokenHash(TokenHashUtil.hash(refreshToken));
        refreshTokenRepoService.save(refreshEntity);

        CookieUtil.addRefreshToCookie(response, refreshToken ,cookieConfig.isProd() );

        return accessToken;
    }
}
