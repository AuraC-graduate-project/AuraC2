package com.server.contestControl.submissionServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidSubmissionRequestException extends ApiException {
    public InvalidSubmissionRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
