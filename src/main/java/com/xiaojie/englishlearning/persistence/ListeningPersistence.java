package com.xiaojie.englishlearning.persistence;

import com.xiaojie.englishlearning.user.ListeningData;
import java.util.function.Function;

/** Durable persistence boundary for one user's complete listening state. */
public interface ListeningPersistence {
    default void checkWritable() { }
    ListeningData load(String userId);

    <T> T update(String userId, Function<ListeningData, T> operation);
}
