package com.server.contestControl.authServer.service.refreshToken;

import com.server.contestControl.authServer.entity.RefreshToken;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.repository.RefreshTokenRepository;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenRepoService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;



    public RefreshToken createAndSave(User user, String deviceIp ){
        RefreshToken saved = refreshTokenRepository.save(
                RefreshToken.builder()
                        .user(user)
                        .deviceIp(deviceIp)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plusMillis(jwtService.getExpiration(TokenType.REFRESH)))
                        .revoked(false)
                        .build()
        );

        return refreshTokenRepository.save(saved);
    }

    public boolean isRevoked(Long Id) {
        return findById(Id)
                .map(RefreshToken::isRevoked)
                .orElse(true);
    }

    public void revokeToken(Long Id) {
        findById(Id).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public Optional<RefreshToken> findById(Long id) {
        return refreshTokenRepository.findById(id);
    }




    public void save(RefreshToken refreshToken) {
        refreshTokenRepository.save(refreshToken);
    }


}
