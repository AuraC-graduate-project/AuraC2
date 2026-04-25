package com.server.contestControl.contestServer.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ContestNotFoundException extends ApiException {
    public ContestNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public ContestNotFoundException(Long contestId) {
        super("Contest not found with ID: " + contestId, HttpStatus.NOT_FOUND);
    }
}
