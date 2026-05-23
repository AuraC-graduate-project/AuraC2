package com.server.contestControl.contestServer.oracle.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class OracleNotFoundException extends ApiException {
    public OracleNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
