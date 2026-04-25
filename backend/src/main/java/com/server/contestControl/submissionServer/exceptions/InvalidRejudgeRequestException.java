package com.server.contestControl.submissionServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidRejudgeRequestException extends ApiException {
    public InvalidRejudgeRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
