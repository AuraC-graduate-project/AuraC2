package com.server.contestControl.authServer.service.logout;

import com.server.contestControl.authServer.config.CookieConfig;
import com.server.contestControl.authServer.dto.refresh.TokenValidationResult;
import com.server.contestControl.authServer.entity.RefreshToken;
import com.server.contestControl.authServer.service.refreshToken.RefreshTokenRepoService;
import com.server.contestControl.authServer.service.refreshToken.RefreshTokenValidator;
import com.server.contestControl.authServer.util.CookieUtil;
import com.server.contestControl.authServer.util.TokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutService {

    private final RefreshTokenValidator refreshTokenValidator;
    private final RefreshTokenRepoService refreshTokenRepoService;
    private final  CookieConfig config;

    public void logout(HttpServletRequest request,
                       HttpServletResponse response) {

        String oldToken = TokenExtractor.extractFromCookie(request);
        TokenValidationResult result = refreshTokenValidator.validate(oldToken);

        RefreshToken stored = result.refreshToken();

        if (!stored.isRevoked()) {
            stored.setRevoked(true);
            refreshTokenRepoService.save(stored);
            log.info("Revoked refresh token {}", stored.getId());
        }

        SecurityContextHolder.clearContext();
        response.setHeader("Authorization", "");
        CookieUtil.clearRefreshCookie(response, config.isProd());
    }
}