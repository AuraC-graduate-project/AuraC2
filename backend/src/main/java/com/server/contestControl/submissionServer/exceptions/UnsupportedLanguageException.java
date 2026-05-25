package com.server.contestControl.submissionServer.exceptions;

import com.server.contestControl.authServer.exception.api.base.ApiException;
import org.springframework.http.HttpStatus;

public class UnsupportedLanguageException extends ApiException {
    public UnsupportedLanguageException(String language) {
        super("Unsupported language: " + language, HttpStatus.BAD_REQUEST);
    }
}
