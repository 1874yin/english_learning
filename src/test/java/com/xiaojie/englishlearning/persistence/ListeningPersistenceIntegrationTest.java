package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.user.ListeningData;
import com.xiaojie.englishlearning.persistence.ListeningPersistence;
import com.xiaojie.englishlearning.persistence.mongodb.ListeningDocumentSizeValidator;
import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"app.translation.enabled=false", "spring.flyway.enabled=true"})
class ListeningPersistenceIntegrationTest extends DatabaseTestContainers {

    @Autowired
    private ListeningPersistence listening;

    @Autowired
    private ListeningDocumentSizeValidator sizeValidator;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void roundTripsCompleteUserStateAndKeepsDeletedLessonSnapshots() throws Exception {
        ListeningData source = new ListeningData();
        ListeningData.Record record = new ListeningData.Record();
        record.lessonId = "deleted-lesson";
        record.title = "Snapshot title";
        record.titleZh = "快照标题";
        record.category = "History";
        record.duration = 120;
        record.position = 88;
        record.listenedSeconds = 95;
        record.updatedAt = 1_762_000_000_000L;
        record.lastListenedAt = "2026-11-03T01:00:00Z";
        source.records.put(record.lessonId, record);
        ListeningData.Stream stream = new ListeningData.Stream();
        stream.lessonId = record.lessonId;
        stream.sequence = 4;
        stream.seconds = 95;
        stream.lastReportedAt = "2026-11-03T01:00:00Z";
        source.streams.put("stream-1", stream);
        source.daily.put("2026-11-03", 95.0);
        source.hours.put("9", 95.0);
        source.favorites.put("deleted-lesson", 1_762_000_000_000L);
        source.lastCreditedAt = "2026-11-03T01:00:00Z";
        source.analysis = mapper.readTree("{\"title\":\"Learner\",\"suggestions\":[\"Keep listening\"]}");

        listening.update("state-roundtrip", current -> {
            current.records.putAll(source.records);
            current.streams.putAll(source.streams);
            current.daily.putAll(source.daily);
            current.hours.putAll(source.hours);
            current.favorites.putAll(source.favorites);
            current.lastCreditedAt = source.lastCreditedAt;
            current.analysis = source.analysis;
            return null;
        });

        ListeningData loaded = listening.load("state-roundtrip");
        assertThat(loaded.records.get("deleted-lesson").title).isEqualTo("Snapshot title");
        assertThat(loaded.records.get("deleted-lesson").listenedSeconds).isEqualTo(95);
        assertThat(loaded.streams.get("stream-1").sequence).isEqualTo(4);
        assertThat(loaded.daily).containsEntry("2026-11-03", 95.0);
        assertThat(loaded.hours).containsEntry("9", 95.0);
        assertThat(loaded.favorites).containsKey("deleted-lesson");
        assertThat(loaded.lastCreditedAt).isEqualTo(source.lastCreditedAt);
        assertThat(loaded.analysis.path("title").asText()).isEqualTo("Learner");
    }

    @Test
    void isolatesDocumentsByUserId() {
        listening.update("user-a", data -> {
            data.favorites.put("lesson-a", 1L);
            return null;
        });
        listening.update("user-b", data -> {
            data.favorites.put("lesson-b", 2L);
            return null;
        });

        assertThat(listening.load("user-a").favorites).containsOnlyKeys("lesson-a");
        assertThat(listening.load("user-b").favorites).containsOnlyKeys("lesson-b");
    }

    @Test
    void concurrentUpdatesRetryFromLatestVersionWithoutLosingFavoritesOrCreditingSequenceTwice() throws Exception {
        listening.update("concurrent-user", data -> {
            ListeningData.Stream stream = new ListeningData.Stream();
            stream.sequence = 0;
            stream.seconds = 0;
            stream.lastReportedAt = "2026-11-03T00:00:00Z";
            data.streams.put("shared-stream", stream);
            return null;
        });

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch loadedSameVersion = new CountDownLatch(3);
        CountDownLatch applyWrites = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> writes = new ArrayList<>();
            writes.add(pool.submit(() -> {
                ready.countDown();
                await(start);
                AtomicBoolean first = new AtomicBoolean(true);
                listening.update("concurrent-user", data -> {
                    holdFirstApply(first, loadedSameVersion, applyWrites);
                    data.favorites.put("favorite-a", 10L);
                    return null;
                });
            }));
            writes.add(pool.submit(() -> {
                ready.countDown();
                await(start);
                AtomicBoolean first = new AtomicBoolean(true);
                listening.update("concurrent-user", data -> {
                    holdFirstApply(first, loadedSameVersion, applyWrites);
                    data.favorites.put("favorite-b", 20L);
                    return null;
                });
            }));
            writes.add(pool.submit(() -> {
                ready.countDown();
                await(start);
                AtomicBoolean first = new AtomicBoolean(true);
                listening.update("concurrent-user", data -> {
                    holdFirstApply(first, loadedSameVersion, applyWrites);
                    ListeningData.Stream stream = data.streams.get("shared-stream");
                    if (stream.sequence < 1) {
                        stream.sequence = 1;
                        stream.seconds = 30;
                        data.daily.merge("2026-11-03", 30.0, Double::sum);
                    }
                    return null;
                });
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(loadedSameVersion.await(5, TimeUnit.SECONDS)).isTrue();
            applyWrites.countDown();
            for (Future<?> write : writes) write.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        ListeningData result = listening.load("concurrent-user");
        assertThat(result.favorites).containsKeys("favorite-a", "favorite-b");
        assertThat(result.streams.get("shared-stream").sequence).isEqualTo(1);
        assertThat(result.daily).containsEntry("2026-11-03", 30.0);
    }

    @Test
    void rejectsAListeningDocumentThatExceedsMongoDbLimitWithUserId() {
        ListeningData oversized = new ListeningData();
        ListeningData.Record record = new ListeningData.Record();
        record.lessonId = "large-record";
        record.title = "x".repeat(ListeningDocumentSizeValidator.MAX_BSON_BYTES);
        oversized.records.put(record.lessonId, record);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sizeValidator.validate("oversized-user", oversized))
                .isInstanceOf(com.xiaojie.englishlearning.user.UserException.class)
                .hasMessageContaining("oversized-user")
                .hasMessageContaining("16 MiB");
    }

    private static void holdFirstApply(AtomicBoolean first, CountDownLatch loadedSameVersion, CountDownLatch applyWrites) {
        if (first.compareAndSet(true, false)) {
            loadedSameVersion.countDown();
            await(applyWrites);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("writer rendezvous timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
