package com.server.contestControl.contestServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class ProblemDoesNotBelongToContestException extends ApiException {
    public ProblemDoesNotBelongToContestException(Long problemId, Long contestId) {
        super("Problem with ID '" + problemId + "' does not belong to contest with ID '" + contestId + "'.", HttpStatus.BAD_REQUEST);
    }
}
