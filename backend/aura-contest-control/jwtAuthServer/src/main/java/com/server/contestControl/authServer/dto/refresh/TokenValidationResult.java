package com.server.contestControl.authServer.dto.refresh;

import com.server.contestControl.authServer.entity.RefreshToken;
import com.server.contestControl.authServer.entity.User;

public record TokenValidationResult(User user, RefreshToken refreshToken , Long refreshTokenId) {}
