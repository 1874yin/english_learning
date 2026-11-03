package com.xiaojie.englishlearning.translation;

/**
 * The provider returned something that cannot be matched one-to-one with the
 * input sentences. Retrying the affected sentences individually is worthwhile.
 */
public class TranslationAlignmentException extends TranslationException {

    public TranslationAlignmentException(String message) {
        super(message);
    }
}
