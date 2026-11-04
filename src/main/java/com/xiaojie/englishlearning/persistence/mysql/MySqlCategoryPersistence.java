package com.xiaojie.englishlearning.persistence.mysql;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaojie.englishlearning.persistence.CategoryPersistence;
import com.xiaojie.englishlearning.persistence.mysql.mapper.CategoryMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "database", matchIfMissing = true)
public class MySqlCategoryPersistence implements CategoryPersistence {

    private final CategoryMapper categories;

    public MySqlCategoryPersistence(CategoryMapper categories) {
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAll() {
        List<String> result = new ArrayList<>();
        for (CategoryEntity entity : categories.selectList(new QueryWrapper<CategoryEntity>().orderByAsc("id"))) {
            result.add(entity.getName());
        }
        return result;
    }

    @Override
    @Transactional
    public String add(String name, List<String> defaults) {
        String key = key(name);
        CategoryEntity existing = categories.selectByNameKey(key);
        if (existing != null) {
            return existing.getName();
        }
        seedDefaultsIfEmpty(defaults);
        existing = categories.selectByNameKey(key);
        if (existing != null) {
            return existing.getName();
        }
        CategoryEntity entity = new CategoryEntity();
        entity.setNameKey(key);
        entity.setName(name);
        try {
            categories.insert(entity);
            return name;
        } catch (DuplicateKeyException ex) {
            CategoryEntity raced = categories.selectByNameKey(key);
            if (raced != null) {
                return raced.getName();
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public boolean remove(String name, List<String> defaults) {
        seedDefaultsIfEmpty(defaults);
        return categories.delete(new QueryWrapper<CategoryEntity>().eq("name_key", key(name))) > 0;
    }

    private void seedDefaultsIfEmpty(List<String> defaults) {
        if (categories.selectCount(new QueryWrapper<>()) == 0) {
            for (String name : defaults) {
                categories.insertIfAbsent(key(name), name);
            }
        }
    }

    private String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
