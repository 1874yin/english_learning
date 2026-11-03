package com.xiaojie.englishlearning.translation;

/**
 * The translation service itself is unusable right now: network failure,
 * invalid credentials, quota exhaustion or a server error. Retrying the next
 * lesson in the same run would only repeat the same failure, so the background
 * scan stops and resumes on the next startup.
 */
public class TranslationUnavailableException extends TranslationException {

    public TranslationUnavailableException(String message) {
        super(message);
    }

    public TranslationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
