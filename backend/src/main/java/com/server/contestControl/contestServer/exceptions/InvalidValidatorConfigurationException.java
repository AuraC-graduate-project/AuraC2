package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidValidatorConfigurationException extends ApiException {
    public InvalidValidatorConfigurationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
