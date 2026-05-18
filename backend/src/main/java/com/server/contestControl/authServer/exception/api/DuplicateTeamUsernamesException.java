package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class DuplicateTeamUsernamesException extends ApiException {
    public DuplicateTeamUsernamesException(List<String> usernames) {
        super("Team usernames already exist: " + String.join(", ", usernames), HttpStatus.CONFLICT);
    }
}
