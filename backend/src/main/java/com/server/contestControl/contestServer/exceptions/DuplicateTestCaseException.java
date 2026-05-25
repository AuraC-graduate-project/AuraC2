package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateTestCaseException extends ApiException {
    public DuplicateTestCaseException() {
        super("A test case with the same input and expected output already exists for this problem.", HttpStatus.CONFLICT);
    }
}
