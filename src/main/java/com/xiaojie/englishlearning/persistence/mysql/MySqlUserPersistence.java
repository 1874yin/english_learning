package com.xiaojie.englishlearning.persistence.mysql;

import com.xiaojie.englishlearning.persistence.UserPersistence;
import com.xiaojie.englishlearning.persistence.mysql.mapper.UserMapper;
import com.xiaojie.englishlearning.user.UserStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "database", matchIfMissing = true)
public class MySqlUserPersistence implements UserPersistence {

    private final UserMapper users;

    public MySqlUserPersistence(UserMapper users) {
        this.users = users;
    }

    @Override
    public UserStore.Account findById(String id) {
        UserEntity entity = users.selectById(id);
        return entity == null ? null : toAccount(entity);
    }

    @Override
    public UserStore.Account findByUsername(String usernameKey) {
        UserEntity entity = users.selectByUsernameKey(usernameKey);
        return entity == null ? null : toAccount(entity);
    }

    @Override
    public void insert(UserStore.Account account) {
        UserEntity entity = new UserEntity();
        entity.setId(account.id);
        entity.setUsername(account.username);
        entity.setUsernameKey(account.username.toLowerCase(java.util.Locale.ROOT));
        entity.setPasswordHash(account.passwordHash);
        entity.setCreatedAt(account.createdAt);
        users.insert(entity);
    }

    private UserStore.Account toAccount(UserEntity entity) {
        UserStore.Account account = new UserStore.Account();
        account.id = entity.getId();
        account.username = entity.getUsername();
        account.passwordHash = entity.getPasswordHash();
        account.createdAt = entity.getCreatedAt();
        return account;
    }
}
