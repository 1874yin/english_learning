package com.xiaojie.englishlearning.persistence.mongodb;

import com.mongodb.MongoClientSettings;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.springframework.stereotype.Component;
import com.xiaojie.englishlearning.user.ListeningData;
import com.xiaojie.englishlearning.user.UserException;
import org.springframework.http.HttpStatus;

@Component
public class ListeningDocumentSizeValidator {
    public static final int MAX_BSON_BYTES = 16 * 1024 * 1024;

    private final ListeningDataMapper mapper;

    public ListeningDocumentSizeValidator(ListeningDataMapper mapper) {
        this.mapper = mapper;
    }

    public int encodedSize(String userId, ListeningData data) {
        org.bson.Document document = mapper.toBsonDocument(userId, data);
        BsonDocument bson = document.toBsonDocument(org.bson.Document.class,
                MongoClientSettings.getDefaultCodecRegistry());
        BasicOutputBuffer output = new BasicOutputBuffer();
        try (BsonBinaryWriter writer = new BsonBinaryWriter(output)) {
            new BsonDocumentCodec().encode(writer, bson,
                    EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        }
        return output.getSize();
    }

    public void validate(String userId, ListeningData data) {
        int size = encodedSize(userId, data);
        if (size > MAX_BSON_BYTES) {
            throw new UserException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "用户 " + userId + " 的收听数据文档超过 MongoDB 16 MiB 限制（" + size + " 字节）。");
        }
    }
}
