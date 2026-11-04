package com.xiaojie.englishlearning.migration;

public class ImportValidationException extends IllegalArgumentException {
    public ImportValidationException(String message) {
        super(message);
    }

    public ImportValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
