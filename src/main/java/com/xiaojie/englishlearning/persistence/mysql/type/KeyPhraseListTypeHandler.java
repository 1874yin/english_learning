package com.xiaojie.englishlearning.persistence.mysql.type;

import com.xiaojie.englishlearning.lesson.KeyPhrase;

public class KeyPhraseListTypeHandler extends JsonListTypeHandler<KeyPhrase> {
    @Override
    protected Class<KeyPhrase> elementType() {
        return KeyPhrase.class;
    }
}
