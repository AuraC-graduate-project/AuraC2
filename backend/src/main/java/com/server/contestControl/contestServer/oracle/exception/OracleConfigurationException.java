package com.server.contestControl.contestServer.oracle.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class OracleConfigurationException extends ApiException {
    public OracleConfigurationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
