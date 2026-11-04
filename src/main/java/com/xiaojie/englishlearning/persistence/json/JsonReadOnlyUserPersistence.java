package com.xiaojie.englishlearning.persistence.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.persistence.ReadOnlyPersistenceException;
import com.xiaojie.englishlearning.persistence.UserPersistence;
import com.xiaojie.englishlearning.storage.StoragePaths;
import com.xiaojie.englishlearning.user.UserStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "json-read-only")
public class JsonReadOnlyUserPersistence implements UserPersistence {
    private final StoragePaths paths;
    private final ObjectMapper mapper;

    public JsonReadOnlyUserPersistence(StoragePaths paths, ObjectMapper mapper) {
        this.paths = paths;
        this.mapper = mapper;
    }

    @Override
    public UserStore.Account findById(String id) {
        return accounts().stream().filter(account -> account.id.equals(id)).findFirst().orElse(null);
    }

    @Override
    public UserStore.Account findByUsername(String usernameKey) {
        String key = usernameKey.toLowerCase(Locale.ROOT);
        return accounts().stream().filter(account -> account.username.toLowerCase(Locale.ROOT).equals(key))
                .findFirst().orElse(null);
    }

    @Override public void insert(UserStore.Account account) { throw new ReadOnlyPersistenceException(); }
    @Override public void checkWritable() { throw new ReadOnlyPersistenceException(); }

    private List<UserStore.Account> accounts() {
        var file = paths.getRoot().resolve("users.json");
        if (!Files.exists(file)) return List.of();
        try {
            List<UserStore.Account> loaded = mapper.readValue(file.toFile(), new TypeReference<List<UserStore.Account>>() { });
            return loaded == null ? List.of() : loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read account snapshot " + file, ex);
        }
    }
}
