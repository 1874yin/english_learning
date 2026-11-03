package com.xiaojie.englishlearning.translation;

/**
 * The background translation job was asked to stop (for example during
 * application shutdown). Callers should stop scanning lessons and leave the
 * remaining work for the next startup.
 */
public class TranslationInterruptedException extends TranslationException {

    public TranslationInterruptedException(String message) {
        super(message);
    }
}
