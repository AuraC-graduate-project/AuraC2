package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidTeamGenerationRequestException extends ApiException {
    public InvalidTeamGenerationRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
