package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ClarificationAlreadyClosedException extends ApiException {
    public ClarificationAlreadyClosedException(Long id) {
        super("Clarification with ID '" + id + "' is already closed. Cannot reply or update.", HttpStatus.BAD_REQUEST);
    }
}
