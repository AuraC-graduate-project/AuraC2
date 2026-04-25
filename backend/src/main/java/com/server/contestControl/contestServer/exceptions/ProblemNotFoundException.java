package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ProblemNotFoundException extends ApiException {
    public ProblemNotFoundException(Long id) {
        super("Problem with ID '" + id + "' not found.", HttpStatus.NOT_FOUND);
    }
}
