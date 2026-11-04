package com.xiaojie.englishlearning.persistence.mysql.type;

import com.xiaojie.englishlearning.lesson.TranscriptLine;

public class TranscriptListTypeHandler extends JsonListTypeHandler<TranscriptLine> {
    @Override
    protected Class<TranscriptLine> elementType() {
        return TranscriptLine.class;
    }
}
