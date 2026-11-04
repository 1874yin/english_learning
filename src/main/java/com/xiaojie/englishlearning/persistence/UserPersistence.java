package com.xiaojie.englishlearning.persistence;

import com.xiaojie.englishlearning.user.UserStore;

public interface UserPersistence {
    default void checkWritable() { }
    UserStore.Account findById(String id);
    UserStore.Account findByUsername(String usernameKey);
    void insert(UserStore.Account account);
}
