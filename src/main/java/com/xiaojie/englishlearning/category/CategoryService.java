package com.xiaojie.englishlearning.category;

import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final CategoryStore categoryStore;
    private final LessonStore lessonStore;

    public CategoryService(CategoryStore categoryStore, LessonStore lessonStore) {
        this.categoryStore = categoryStore;
        this.lessonStore = lessonStore;
    }

    /**
     * Suggested categories plus every category currently used by a lesson.
     * This makes a custom category typed on a lesson available for filtering
     * immediately, even if it was never added to the suggestion list.
     */
    public List<String> list() {
        List<String> result = new ArrayList<String>(categoryStore.findAll());
        for (Lesson lesson : lessonStore.findAll()) {
            String category = normalize(lesson.getCategory());
            if (category == null || containsIgnoreCase(result, category)) {
                continue;
            }
            result.add(category);
        }
        return result;
    }

    public List<CategorySummary> browse(String level) {
        String requestedLevel = normalize(level);
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Lesson lesson : lessonStore.findAll()) {
            String category = normalize(lesson.getCategory());
            if (category == null || (requestedLevel != null
                    && !requestedLevel.equalsIgnoreCase(normalize(lesson.getLevel())))) {
                continue;
            }
            String key = category.toLowerCase(Locale.ROOT);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        List<CategorySummary> result = new ArrayList<CategorySummary>();
        for (String category : list()) {
            Integer count = counts.get(category.toLowerCase(Locale.ROOT));
            if (count != null) {
                result.add(new CategorySummary(category, count));
            }
        }
        return result;
    }

    public static class CategorySummary {

        private final String name;
        private final int count;

        public CategorySummary(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    public void checkWritable() {
        categoryStore.checkWritable();
    }

    public String add(String name) throws IOException {
        return categoryStore.add(name);
    }

    public boolean remove(String name) throws IOException {
        return categoryStore.remove(name);
    }

    public boolean isUsed(String name) {
        String normalized = normalize(name);
        if (normalized == null) {
            return false;
        }
        for (Lesson lesson : lessonStore.findAll()) {
            String category = normalize(lesson.getCategory());
            if (category != null && category.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(List<String> names, String name) {
        for (String existing : names) {
            if (existing.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
