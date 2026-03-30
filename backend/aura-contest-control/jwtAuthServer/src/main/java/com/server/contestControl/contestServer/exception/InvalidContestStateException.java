package com.server.contestControl.contestServer.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidContestStateException extends ApiException {
    public InvalidContestStateException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
