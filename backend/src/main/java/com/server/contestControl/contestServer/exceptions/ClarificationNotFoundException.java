package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ClarificationNotFoundException extends ApiException {
    public ClarificationNotFoundException(Long id) {
        super("Clarification with ID '" + id + "' not found.", HttpStatus.NOT_FOUND);
    }
}
