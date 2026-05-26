package com.server.contestControl.submissionServer.run.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class RunRateLimitException extends ApiException {
    public RunRateLimitException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
