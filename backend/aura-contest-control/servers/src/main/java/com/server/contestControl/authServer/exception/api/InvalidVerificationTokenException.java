package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends ApiException {

    public InvalidVerificationTokenException() {
        super("Invalid verification token provided.", HttpStatus.BAD_REQUEST);
    }

    public InvalidVerificationTokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}