package com.xiaojie.englishlearning.persistence;

import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonPage;
import java.util.List;
import java.util.Optional;

public interface LessonPersistence {
    default void checkWritable() { }
    List<Lesson> findAll();
    Optional<Lesson> findById(String id);
    String nextNumber();
    Lesson insert(Lesson lesson);
    Lesson replace(Lesson lesson);
    boolean updateTranslations(String id, List<String> expectedTexts, List<String> translations);
    int clearAllTranslations();
    LessonPage findPage(int page, int size);
}
