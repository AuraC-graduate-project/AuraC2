package com.server.contestControl.authServer.service.refreshToken;

import com.server.contestControl.authServer.dto.refresh.RefreshResponse;
import com.server.contestControl.authServer.dto.refresh.TokenValidationResult;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.MissingTokenException;
import com.server.contestControl.authServer.util.TokenExtractor;
import com.server.contestControl.authServer.util.TokenIssuerUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenValidator refreshTokenValidator;
    private final RefreshTokenRepoService refreshTokenRepoService;
    private final TokenIssuerUtil tokenIssuerUtil;

    @Transactional
    public RefreshResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String oldToken = TokenExtractor.extractFromCookie(request);
        if (oldToken == null) throw new MissingTokenException();

        TokenValidationResult result = refreshTokenValidator.validate(oldToken);
        User user = result.user();

        refreshTokenRepoService.revokeToken(result.refreshTokenId());

        String newAccessToken = tokenIssuerUtil.issueTokens(user, request.getRemoteAddr(), response);

        return new RefreshResponse(newAccessToken);
    }
}
