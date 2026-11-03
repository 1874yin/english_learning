package com.xiaojie.englishlearning.translation;

import java.util.List;

public interface TranslationProvider {

    /**
     * Translates each sentence and returns one Chinese translation per input
     * sentence, in the same order. Implementations must never merge or split
     * sentences; an alignment problem is reported as
     * {@link TranslationAlignmentException} so the caller can retry sentence by
     * sentence.
     */
    List<String> translate(List<String> sentences);
}
