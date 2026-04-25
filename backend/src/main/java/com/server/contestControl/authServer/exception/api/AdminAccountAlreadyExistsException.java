package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class AdminAccountAlreadyExistsException extends ApiException {
    public AdminAccountAlreadyExistsException() {
        super("Only one admin account is allowed.", HttpStatus.CONFLICT);
    }
}
