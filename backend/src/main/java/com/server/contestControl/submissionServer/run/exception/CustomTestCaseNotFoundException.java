package com.server.contestControl.submissionServer.run.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class CustomTestCaseNotFoundException extends ApiException {
    public CustomTestCaseNotFoundException() {
        super("Custom test case not found.", HttpStatus.NOT_FOUND);
    }
}
