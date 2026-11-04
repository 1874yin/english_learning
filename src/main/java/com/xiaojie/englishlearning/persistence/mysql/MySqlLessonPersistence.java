package com.xiaojie.englishlearning.persistence.mysql;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaojie.englishlearning.lesson.KeyPhrase;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonConflictException;
import com.xiaojie.englishlearning.lesson.LessonPage;
import com.xiaojie.englishlearning.lesson.LessonSummary;
import com.xiaojie.englishlearning.lesson.TranscriptLine;
import com.xiaojie.englishlearning.persistence.LessonPersistence;
import com.xiaojie.englishlearning.persistence.mysql.mapper.LessonMapper;
import com.xiaojie.englishlearning.persistence.mysql.mapper.LessonNumberSequenceMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "database", matchIfMissing = true)
public class MySqlLessonPersistence implements LessonPersistence {

    private final LessonMapper lessons;
    private final LessonNumberSequenceMapper sequences;

    public MySqlLessonPersistence(LessonMapper lessons, LessonNumberSequenceMapper sequences) {
        this.lessons = lessons;
        this.sequences = sequences;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lesson> findAll() {
        List<Lesson> result = new ArrayList<>();
        for (LessonEntity entity : lessons.selectList(new QueryWrapper<LessonEntity>().orderByAsc("sort_order"))) {
            result.add(toDomain(entity));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Lesson> findById(String id) {
        LessonEntity entity = lessons.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public String nextNumber() {
        LessonNumberSequenceEntity sequence = sequences.selectById(1);
        if (sequence == null) {
            throw new IllegalStateException("Lesson number sequence row is missing");
        }
        return formatNumber(sequence.getNextNumber());
    }

    @Override
    @Transactional
    public Lesson insert(Lesson lesson) {
        LessonNumberSequenceEntity sequence = lessons.lockNumberSequence();
        if (sequence == null) {
            throw new IllegalStateException("Lesson number sequence row is missing");
        }
        int allocatedNumber = sequence.getNextNumber();
        long allocatedSortOrder = sequence.getNextSortOrder();
        sequence.setNextNumber(allocatedNumber + 1);
        sequence.setNextSortOrder(allocatedSortOrder + 1);
        sequences.updateById(sequence);

        lesson.setNumber(formatNumber(allocatedNumber));
        LessonEntity entity = toEntity(lesson);
        entity.setSortOrder(allocatedSortOrder);
        entity.setVersion(0L);
        lessons.insert(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional
    public Lesson replace(Lesson lesson) {
        LessonEntity current = lessons.selectById(lesson.getId());
        if (current == null) {
            throw new NoSuchElementException("素材不存在：" + lesson.getId());
        }
        Long expectedVersion = lesson.getPersistenceVersion();
        if (expectedVersion == null || !expectedVersion.equals(current.getVersion())) {
            throw new LessonConflictException("素材已被其他请求更新，请刷新后重试。");
        }

        LessonEntity replacement = toEntity(lesson);
        replacement.setSortOrder(current.getSortOrder());
        replacement.setVersion(expectedVersion);
        if (lessons.updateById(replacement) != 1) {
            throw new LessonConflictException("素材已被其他请求更新，请刷新后重试。");
        }
        lesson.setPersistenceVersion(replacement.getVersion());
        return lesson;
    }

    @Override
    @Transactional
    public boolean updateTranslations(String id, List<String> expectedTexts, List<String> translations) {
        LessonEntity entity = lessons.selectById(id);
        if (entity == null || expectedTexts == null || translations == null) {
            return false;
        }
        List<TranscriptLine> lines = entity.getTranscript();
        if (lines == null || lines.size() != expectedTexts.size() || translations.size() != expectedTexts.size()) {
            return false;
        }
        for (int i = 0; i < lines.size(); i++) {
            TranscriptLine line = lines.get(i);
            if (line == null || line.getText() == null || !line.getText().equals(expectedTexts.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).setTranslation(translations.get(i));
        }
        if (lessons.updateById(entity) != 1) {
            throw new LessonConflictException("素材已被其他请求更新，请刷新后重试。");
        }
        return true;
    }

    @Override
    @Transactional
    public int clearAllTranslations() {
        int cleared = 0;
        for (LessonEntity entity : lessons.selectList(new QueryWrapper<>())) {
            List<TranscriptLine> lines = entity.getTranscript();
            if (lines == null) {
                continue;
            }
            boolean changed = false;
            for (TranscriptLine line : lines) {
                if (line != null && line.getTranslation() != null) {
                    line.setTranslation(null);
                    cleared++;
                    changed = true;
                }
            }
            if (changed && lessons.updateById(entity) != 1) {
                throw new LessonConflictException("素材已被其他请求更新，请刷新后重试。");
            }
        }
        return cleared;
    }

    @Override
    @Transactional(readOnly = true)
    public LessonPage findPage(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        long total = lessons.countLessons();
        long offset = (long) safePage * safeSize;
        List<LessonSummary> items = new ArrayList<>();
        for (LessonSummaryRow row : lessons.selectSummaries(offset, safeSize)) {
            items.add(new LessonSummary(row.getId(), row.getNumber(), row.getTitle(), row.getTitleZh(),
                    row.getCategory(), row.getLevel(), row.getDuration(), row.getCover()));
        }
        return new LessonPage(items, safePage, safeSize, (int) total);
    }

    private LessonEntity toEntity(Lesson lesson) {
        LessonEntity entity = new LessonEntity();
        entity.setId(lesson.getId());
        entity.setNumber(lesson.getNumber());
        entity.setTitle(lesson.getTitle());
        entity.setTitleZh(lesson.getTitleZh());
        entity.setCategory(lesson.getCategory());
        entity.setLevel(lesson.getLevel());
        entity.setDuration(lesson.getDuration());
        entity.setAudio(lesson.getAudio());
        entity.setCover(lesson.getCover());
        entity.setSummary(lesson.getSummary());
        entity.setSummaryZh(lesson.getSummaryZh());
        entity.setUpdatedAt(lesson.getUpdatedAt());
        entity.setTranscript(lesson.getTranscript() == null ? Collections.emptyList() : lesson.getTranscript());
        entity.setWordsText(lesson.getWordsText());
        List<KeyPhrase> phrases = lesson.getKeyPhrases() == null ? Collections.emptyList() : lesson.getKeyPhrases();
        entity.setKeyPhrases(phrases);
        entity.setVersion(lesson.getPersistenceVersion());
        return entity;
    }

    private Lesson toDomain(LessonEntity entity) {
        Lesson lesson = new Lesson();
        lesson.setId(entity.getId());
        lesson.setNumber(entity.getNumber());
        lesson.setTitle(entity.getTitle());
        lesson.setTitleZh(entity.getTitleZh());
        lesson.setCategory(entity.getCategory());
        lesson.setLevel(entity.getLevel());
        lesson.setDuration(entity.getDuration());
        lesson.setAudio(entity.getAudio());
        lesson.setCover(entity.getCover());
        lesson.setSummary(entity.getSummary());
        lesson.setSummaryZh(entity.getSummaryZh());
        lesson.setUpdatedAt(entity.getUpdatedAt());
        lesson.setTranscript(entity.getTranscript() == null ? Collections.emptyList() : entity.getTranscript());
        lesson.setWordsText(entity.getWordsText());
        lesson.setKeyPhrases(entity.getKeyPhrases() == null ? Collections.emptyList() : entity.getKeyPhrases());
        lesson.setPersistenceVersion(entity.getVersion());
        return lesson;
    }

    private String formatNumber(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }
}
