package com.xiaojie.englishlearning.persistence.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ListeningMongoRepository extends MongoRepository<ListeningDocument, String> {
}
