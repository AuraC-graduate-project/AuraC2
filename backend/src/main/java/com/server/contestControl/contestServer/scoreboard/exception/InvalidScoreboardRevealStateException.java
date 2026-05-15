package com.server.contestControl.contestServer.scoreboard.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidScoreboardRevealStateException extends ApiException {
    public InvalidScoreboardRevealStateException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
