package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidDifficultyException extends ApiException {
    public InvalidDifficultyException(String value) {
        super("Invalid difficulty: " + value + ". Valid values: EASY, MEDIUM, HARD", HttpStatus.BAD_REQUEST);
    }
}
