package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidBalloonColorException extends ApiException {
    public InvalidBalloonColorException(String value) {
        super("Invalid balloon color: " + value + ". Use a hex color like #2563EB.", HttpStatus.BAD_REQUEST);
    }
}
