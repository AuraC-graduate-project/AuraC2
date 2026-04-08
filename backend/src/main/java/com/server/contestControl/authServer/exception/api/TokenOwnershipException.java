package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class TokenOwnershipException extends ApiException {

    public TokenOwnershipException() {
        super("Token does not belong to the authenticated user",
                HttpStatus.FORBIDDEN);
    }

    public TokenOwnershipException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}