package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class TestCaseNotFoundException extends ApiException {
    public TestCaseNotFoundException(Long id) {
        super("Test case with ID '" + id + "' not found.", HttpStatus.NOT_FOUND);
    }
}
