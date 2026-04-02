package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class VerificationTokenExpiredException extends ApiException {

    public VerificationTokenExpiredException() {
        super("Email verification token has expired.", HttpStatus.UNAUTHORIZED);
    }

    public VerificationTokenExpiredException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
