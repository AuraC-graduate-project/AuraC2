package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super("Invalid email or password.", HttpStatus.UNAUTHORIZED);
    }
}
