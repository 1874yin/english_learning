package com.xiaojie.englishlearning.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonStore;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ListeningStore {
    private final Path directory;
    private final ObjectMapper mapper;
    private final LessonStore lessons;
    private final Clock clock;
    private final ZoneId zone;

    public ListeningStore(StoragePaths paths, ObjectMapper mapper, LessonStore lessons, Clock clock,
            @Value("${app.user.time-zone:Asia/Shanghai}") String zone) throws IOException {
        this.directory = paths.getRoot().resolve("user-data");
        Files.createDirectories(directory);
        this.mapper = mapper;
        this.lessons = lessons;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public synchronized String start(String userId, String lessonId) throws IOException {
        Lesson lesson = requireLesson(lessonId);
        ListeningData data = read(userId);
        while (data.streams.size() >= 32) data.streams.remove(data.streams.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        ListeningData.Stream stream = new ListeningData.Stream();
        stream.lessonId = lessonId;
        stream.lastReportedAt = now.toString();
        data.streams.put(id, stream);
        // Pressing play is enough to appear in the history; listening time is
        // still only credited from heartbeats.
        ListeningData.Record record = data.records.computeIfAbsent(lesson.getId(), key -> describe(lesson));
        record.updatedAt = now.toEpochMilli();
        record.lastListenedAt = now.toString();
        save(userId, data);
        return id;
    }

    public synchronized void record(String userId, String streamId, int sequence, double seconds, double position) throws IOException {
        if (sequence < 1 || !Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(position) || position < 0) {
            throw new UserException(HttpStatus.BAD_REQUEST, "收听记录格式不正确。" );
        }
        ListeningData data = read(userId);
        ListeningData.Stream stream = data.streams.get(streamId);
        if (stream == null) throw new UserException(HttpStatus.NOT_FOUND, "收听会话已过期，请重新开始播放。" );
        if (sequence <= stream.sequence) return;
        if (seconds < stream.seconds) throw new UserException(HttpStatus.BAD_REQUEST, "收听时长不能倒退。" );
        Lesson lesson = requireLesson(stream.lessonId);
        Instant now = clock.instant();
        Instant last = Instant.parse(stream.lastReportedAt);
        double available = Math.max(0, Duration.between(last, now).toMillis() / 1000.0);
        if (data.lastCreditedAt != null) {
            available = Math.min(available, Math.max(0,
                    Duration.between(Instant.parse(data.lastCreditedAt), now).toMillis() / 1000.0));
        }
        double credited = Math.min(Math.min(seconds - stream.seconds, available), 300);
        ListeningData.Record record = data.records.computeIfAbsent(lesson.getId(), id -> describe(lesson));
        record.position = Math.min(position, Math.max(0, lesson.getDuration()));
        record.updatedAt = now.toEpochMilli();
        if (credited > 0) {
            record.listenedSeconds += credited;
            record.lastListenedAt = now.toString();
            record.imported = false;
            addTime(data, now.minusMillis(Math.round(credited * 1000)), now);
            data.lastCreditedAt = now.toString();
        }
        stream.sequence = sequence;
        stream.seconds = seconds;
        stream.lastReportedAt = now.toString();
        save(userId, data);
    }

    public synchronized void importProgress(String userId, List<ImportRecord> records) throws IOException {
        if (records == null || records.size() > 1000) {
            throw new UserException(HttpStatus.BAD_REQUEST, "每次最多同步 1000 条本地记录。" );
        }
        ListeningData data = read(userId);
        for (ImportRecord input : records) {
            if (input == null || !Double.isFinite(input.position) || input.position < 0 || input.updatedAt < 0) {
                throw new UserException(HttpStatus.BAD_REQUEST, "本地记录格式不正确。" );
            }
            Optional<Lesson> found = lessons.findById(input.lessonId);
            if (!found.isPresent()) continue;
            Lesson lesson = found.get();
            long updatedAt = Math.min(input.updatedAt, clock.millis());
            ListeningData.Record existing = data.records.get(input.lessonId);
            if (existing != null && existing.updatedAt >= updatedAt) continue;
            if (existing == null) {
                // A zero-position progress entry with a timestamp means the
                // lesson was opened but never played, so it is not history.
                // History entries import without a timestamp (updatedAt 0).
                if (input.position <= 0 && input.updatedAt > 0) continue;
                existing = describe(lesson);
                existing.imported = true;
                data.records.put(lesson.getId(), existing);
            }
            existing.position = Math.min(input.position, Math.max(0, lesson.getDuration()));
            existing.updatedAt = updatedAt;
        }
        save(userId, data);
    }

    public synchronized Map<String, Object> profile(String userId) throws IOException {
        ListeningData data = read(userId);
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> stats = new LinkedHashMap<>();
        double total = 0;
        int effective = 0, listened = 0;
        Map<String, Map<String, Object>> categories = new LinkedHashMap<>();
        List<ListeningData.Record> sorted = new ArrayList<>(data.records.values());
        sorted.sort(Comparator.comparingLong((ListeningData.Record record) -> record.updatedAt).reversed());
        List<Map<String, Object>> records = new ArrayList<>();
        for (ListeningData.Record record : sorted) {
            boolean completed = record.duration > 0 && record.listenedSeconds + 0.000001 >= record.duration * 0.8;
            if (completed) effective++;
            total += record.listenedSeconds;
            Map<String, Object> view = mapper.convertValue(record, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            view.put("effective", completed);
            view.put("available", lessons.findById(record.lessonId).isPresent());
            records.add(view);
            if (record.listenedSeconds <= 0) continue;
            listened++;
            String category = record.category == null || record.category.trim().isEmpty() ? "未分类" : record.category;
            Map<String, Object> row = categories.computeIfAbsent(category, name -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("name", name); value.put("seconds", 0.0); value.put("episodes", 0);
                return value;
            });
            row.put("seconds", (double) row.get("seconds") + record.listenedSeconds);
            row.put("episodes", (int) row.get("episodes") + 1);
        }
        String firstDay = data.daily.isEmpty() ? null : data.daily.firstKey();
        long days = firstDay == null ? 0 : Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(firstDay),
                clock.instant().atZone(zone).toLocalDate()) + 1);
        stats.put("totalSeconds", total);
        stats.put("effectiveEpisodes", effective);
        stats.put("listenedEpisodes", listened);
        stats.put("categoryCount", categories.size());
        stats.put("dailyAverageSeconds", days == 0 ? 0 : total / days);
        stats.put("naturalDays", days);
        stats.put("activeDays", data.daily.size());
        stats.put("firstListeningDate", firstDay);
        stats.put("lastListenedAt", data.lastCreditedAt);
        stats.put("preferredPeriod", preferredPeriod(data.hours));
        stats.put("timeZone", zone.getId());
        stats.put("analysisEligible", effective >= 10);
        List<Map<String, Object>> categoryRows = new ArrayList<>(categories.values());
        categoryRows.sort(Comparator.comparingDouble((Map<String, Object> row) -> (double) row.get("seconds")).reversed());
        for (Map<String, Object> row : categoryRows) row.put("share", total > 0 ? (double) row.get("seconds") / total : 0);
        profile.put("stats", stats);
        profile.put("categories", categoryRows);
        profile.put("records", records);
        profile.put("favorites", favorites(data));
        profile.put("daily", data.daily);
        profile.put("hours", data.hours);
        profile.put("analysis", data.analysis);
        return profile;
    }

    public synchronized void favorite(String userId, String lessonId) throws IOException {
        Lesson lesson = requireLesson(lessonId);
        ListeningData data = read(userId);
        if (data.favorites.putIfAbsent(lesson.getId(), clock.millis()) == null) save(userId, data);
    }

    public synchronized void unfavorite(String userId, String lessonId) throws IOException {
        ListeningData data = read(userId);
        if (data.favorites.remove(lessonId) != null) save(userId, data);
    }

    public synchronized void saveAnalysis(String userId, JsonNode analysis) throws IOException {
        ListeningData data = read(userId);
        data.analysis = analysis;
        save(userId, data);
    }

    private List<Map<String, Object>> favorites(ListeningData data) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(data.favorites.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        List<Map<String, Object>> favorites = new ArrayList<>();
        for (Map.Entry<String, Long> entry : entries) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("lessonId", entry.getKey());
            view.put("favoritedAt", Instant.ofEpochMilli(entry.getValue()).toString());
            Optional<Lesson> lesson = lessons.findById(entry.getKey());
            view.put("available", lesson.isPresent());
            if (lesson.isPresent()) {
                Lesson found = lesson.get();
                view.put("title", found.getTitle());
                view.put("titleZh", found.getTitleZh());
                view.put("category", found.getCategory());
                view.put("duration", found.getDuration());
                view.put("cover", found.getCover());
            }
            favorites.add(view);
        }
        return favorites;
    }

    private ListeningData read(String userId) throws IOException {
        Path file = directory.resolve(userId + ".json");
        ListeningData data = Files.exists(file) ? mapper.readValue(file.toFile(), ListeningData.class) : new ListeningData();
        if (data.favorites == null) data.favorites = new LinkedHashMap<>();
        return data;
    }

    private void save(String userId, ListeningData data) throws IOException {
        Path file = directory.resolve(userId + ".json");
        Path temporary = directory.resolve(userId + ".json.tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), data);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Lesson requireLesson(String id) {
        return lessons.findById(id).orElseThrow(() -> new UserException(HttpStatus.NOT_FOUND, "课程不存在或已下架。" ));
    }

    private ListeningData.Record describe(Lesson lesson) {
        ListeningData.Record record = new ListeningData.Record();
        record.lessonId = lesson.getId(); record.title = lesson.getTitle(); record.titleZh = lesson.getTitleZh();
        record.category = lesson.getCategory(); record.duration = lesson.getDuration();
        return record;
    }

    private void addTime(ListeningData data, Instant start, Instant end) {
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            ZonedDateTime local = cursor.atZone(zone);
            Instant boundary = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
            Instant until = boundary.isBefore(end) ? boundary : end;
            double seconds = Duration.between(cursor, until).toMillis() / 1000.0;
            data.daily.merge(local.toLocalDate().toString(), seconds, Double::sum);
            data.hours.merge(String.valueOf(local.getHour()), seconds, Double::sum);
            cursor = until;
        }
    }

    private String preferredPeriod(Map<String, Double> hours) {
        double[] periods = new double[4];
        for (Map.Entry<String, Double> hour : hours.entrySet()) periods[Integer.parseInt(hour.getKey()) / 6] += hour.getValue();
        int best = 0;
        for (int i = 1; i < periods.length; i++) if (periods[i] > periods[best]) best = i;
        return periods[best] == 0 ? "暂无记录" : new String[]{"凌晨 · 00:00–06:00", "上午 · 06:00–12:00", "下午 · 12:00–18:00", "晚间 · 18:00–24:00"}[best];
    }

    public static class ImportRecord {
        public String lessonId;
        public double position;
        public long updatedAt;
    }
}
