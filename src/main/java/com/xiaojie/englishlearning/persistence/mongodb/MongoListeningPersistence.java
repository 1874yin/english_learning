package com.xiaojie.englishlearning.persistence.mongodb;

import com.xiaojie.englishlearning.persistence.ListeningPersistence;
import com.xiaojie.englishlearning.user.ListeningData;
import com.xiaojie.englishlearning.user.UserException;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "database", matchIfMissing = true)
public class MongoListeningPersistence implements ListeningPersistence {
    private static final int MAX_ATTEMPTS = 5;

    private final ListeningMongoRepository repository;
    private final ListeningDataMapper mapper;
    private final ListeningDocumentSizeValidator sizeValidator;

    public MongoListeningPersistence(ListeningMongoRepository repository, ListeningDataMapper mapper,
            ListeningDocumentSizeValidator sizeValidator) {
        this.repository = repository;
        this.mapper = mapper;
        this.sizeValidator = sizeValidator;
    }

    @Override
    public ListeningData load(String userId) {
        return repository.findById(userId).map(mapper::toDomain).orElseGet(ListeningData::new);
    }

    @Override
    public <T> T update(String userId, Function<ListeningData, T> operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ListeningDocument document = repository.findById(userId).orElse(null);
            ListeningData data = mapper.toDomain(document);
            T result = operation.apply(data);
            sizeValidator.validate(userId, data);
            try {
                repository.save(mapper.toDocument(userId, data, document == null ? null : document.getVersion()));
                return result;
            } catch (OptimisticLockingFailureException | DuplicateKeyException conflict) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new UserException(HttpStatus.CONFLICT,
                            "收听数据发生并发冲突，请重试。" );
                }
            }
        }
        throw new IllegalStateException("Unreachable listening update retry state");
    }
}
