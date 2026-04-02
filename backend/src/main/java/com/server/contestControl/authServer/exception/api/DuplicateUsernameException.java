package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateUsernameException extends ApiException {
    public DuplicateUsernameException(String username) {
        super("The email '" + username + "' is already registered.", HttpStatus.CONFLICT);
    }
}
