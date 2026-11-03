package com.xiaojie.englishlearning.category;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.admin.AdminValidationException;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * Stores the suggested lesson categories. Categories used by lessons are added
 * to the public list on top of these, so a custom category typed into the admin
 * form is also remembered without an explicit extra request.
 */
@Service
public class CategoryStore {

    private static final List<String> DEFAULT_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "Listening", "Daily Life", "Culture", "Science", "Health", "Business", "Story"));
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_CATEGORIES = 100;

    private final StoragePaths paths;
    private final ObjectMapper objectMapper;
    private final List<String> categories = new ArrayList<String>();

    public CategoryStore(StoragePaths paths, ObjectMapper objectMapper) {
        this.paths = paths;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void load() {
        categories.clear();
        Path file = paths.getCategoriesFile();
        if (!Files.exists(file)) {
            categories.addAll(DEFAULT_CATEGORIES);
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            List<String> loaded = objectMapper.readValue(input, new TypeReference<List<String>>() { });
            if (loaded != null) {
                for (String category : loaded) {
                    String normalized = normalize(category);
                    if (normalized != null && !containsIgnoreCase(normalized)) {
                        categories.add(normalized);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load lesson categories from " + file, ex);
        }
    }

    public synchronized List<String> findAll() {
        return new ArrayList<String>(categories);
    }

    /**
     * Adds a category unless an equivalent name already exists.
     *
     * @return the name as it is stored, which keeps the existing spelling on a
     *         case-insensitive duplicate.
     */
    public synchronized String add(String rawName) throws IOException {
        String name = requireName(rawName);
        String existing = findByIgnoreCase(name);
        if (existing != null) {
            return existing;
        }
        if (categories.size() >= MAX_CATEGORIES) {
            throw new AdminValidationException("最多保存 " + MAX_CATEGORIES + " 个分类。");
        }
        categories.add(name);
        try {
            persist();
        } catch (IOException ex) {
            categories.remove(categories.size() - 1);
            throw ex;
        }
        return name;
    }

    /**
     * Removes a category from the suggestion list. Lessons that still use the
     * category keep showing it through the merged public list.
     */
    public synchronized boolean remove(String rawName) throws IOException {
        String name = normalize(rawName);
        if (name == null) {
            return false;
        }
        for (int i = 0; i < categories.size(); i += 1) {
            if (categories.get(i).equalsIgnoreCase(name)) {
                String removed = categories.remove(i);
                try {
                    persist();
                } catch (IOException ex) {
                    categories.add(i, removed);
                    throw ex;
                }
                return true;
            }
        }
        return false;
    }

    private String findByIgnoreCase(String name) {
        for (String category : categories) {
            if (category.equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(String name) {
        return findByIgnoreCase(name) != null;
    }

    private String requireName(String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            throw new AdminValidationException("请填写分类名称。");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new AdminValidationException("分类名称请控制在 " + MAX_NAME_LENGTH + " 个字符以内。");
        }
        return name;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void persist() throws IOException {
        Path file = paths.getCategoriesFile();
        Path temporary = paths.getRoot().resolve("categories.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), categories);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
