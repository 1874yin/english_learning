package com.xiaojie.englishlearning.persistence;

import java.util.List;

public interface CategoryPersistence {
    default void checkWritable() { }
    List<String> findAll();
    String add(String name, List<String> defaults);
    boolean remove(String name, List<String> defaults);
}
