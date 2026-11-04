package com.xiaojie.englishlearning.persistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ReadOnlyPersistenceException extends ResponseStatusException {
    public ReadOnlyPersistenceException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "JSON rollback mode is read-only; persistence mutations are disabled.");
    }
}
