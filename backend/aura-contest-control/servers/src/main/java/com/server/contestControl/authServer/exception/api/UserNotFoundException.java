package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException(String username) {
        super("User with username '" + username + "' not found.", HttpStatus.NOT_FOUND);
    }
}
