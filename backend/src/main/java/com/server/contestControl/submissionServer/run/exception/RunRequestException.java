package com.server.contestControl.submissionServer.run.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class RunRequestException extends ApiException {
    public RunRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
