package com.server.contestControl.contestServer.moderation.exception;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ContestTeamModerationException extends ApiException {
    public ContestTeamModerationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public ContestTeamModerationException(String message, HttpStatus status) {
        super(message, status);
    }
}
