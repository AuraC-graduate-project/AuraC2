package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ProblemDeletionConflictException extends ApiException {
    public ProblemDeletionConflictException(Long problemId, String reason) {
        super("Problem with ID '" + problemId + "' cannot be deleted: " + reason, HttpStatus.CONFLICT);
    }
}
