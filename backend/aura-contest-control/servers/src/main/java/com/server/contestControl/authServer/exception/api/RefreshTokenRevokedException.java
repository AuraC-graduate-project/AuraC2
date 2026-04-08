package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class RefreshTokenRevokedException extends ApiException {
    public RefreshTokenRevokedException() {
        super("Refresh token has been revoked.", HttpStatus.UNAUTHORIZED);
    }
}
