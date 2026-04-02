package com.server.contestControl.authServer.exception.api;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class AdminAccountDeletionNotAllowedException extends ApiException {
    public AdminAccountDeletionNotAllowedException() {
        super("The bootstrap admin account cannot be deleted.", HttpStatus.FORBIDDEN);
    }
}
