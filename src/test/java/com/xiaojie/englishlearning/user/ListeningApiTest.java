package com.xiaojie.englishlearning.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonStore;
import com.xiaojie.englishlearning.persistence.ListeningPersistence;
import com.xiaojie.englishlearning.persistence.mongodb.ListeningMongoRepository;
import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"app.translation.enabled=false", "app.translation.deepseek-api-key=translation-only-test-key", "app.user-analysis.api-key=", "app.user.time-zone=Asia/Shanghai", "spring.flyway.enabled=true"})
@AutoConfigureMockMvc
class ListeningApiTest extends DatabaseTestContainers {
    private static final Path ROOT = createRoot();
    private static final MutableClock TIME = new MutableClock();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("app.storage.root", ROOT::toString); }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock testListeningClock() { return TIME; }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired LessonStore lessonStore;
    @Autowired ListeningPersistence listening;
    @Autowired ListeningMongoRepository listeningMongoRepository;

    @BeforeEach
    void resetPersistence() throws Exception {
        listeningMongoRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM lessons");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("UPDATE lesson_number_sequence SET next_number = 1, next_sort_order = 1 WHERE id = 1");
        for (int i = 1; i <= 12; i++) {
            Lesson lesson = new Lesson();
            lesson.setId("lesson" + i);
            lesson.setNumber(String.format("%02d", i));
            lesson.setTitle("Lesson " + i);
            lesson.setCategory(i % 2 == 0 ? "Culture" : "Science");
            lesson.setLevel("Beginner");
            lesson.setDuration(100);
            lesson.setAudio("/media/lesson" + i + ".mp3");
            lesson.setUpdatedAt("2026-10-20T00:00:00Z");
            lessonStore.insert(lesson);
        }
    }

    @Test
    void realListeningNotPositionControlsTheEightyPercentThreshold() throws Exception {
        TIME.set("2026-10-20T10:00:00Z");
        Client c = client();
        String stream = start(c, "lesson1");
        TIME.advance(79);
        heartbeat(c, stream, 1, 79, 99);
        JsonNode before = profile(c);
        assertThat(before.path("stats").path("totalSeconds").asDouble()).isEqualTo(79);
        assertThat(before.path("stats").path("effectiveEpisodes").asInt()).isZero();
        TIME.advance(1);
        heartbeat(c, stream, 2, 80, 2);
        JsonNode after = profile(c);
        assertThat(after.path("stats").path("effectiveEpisodes").asInt()).isEqualTo(1);
        assertThat(after.path("records").get(0).path("position").asDouble()).isEqualTo(2);
        assertThat(after.path("stats").path("categoryCount").asInt()).isEqualTo(1);
        TIME.advance(20);
        heartbeat(c, stream, 3, 100, 22);
        assertThat(profile(c).path("stats").path("effectiveEpisodes").asInt()).isEqualTo(1);
    }

    @Test
    void retriesAndConcurrentTabsDoNotDoubleCountAndServerCapsForgedTime() throws Exception {
        TIME.set("2026-10-21T10:00:00Z");
        Client c = client();
        String a = start(c, "lesson1"), b = start(c, "lesson2");
        TIME.advance(10);
        heartbeat(c, a, 1, 10, 10);
        heartbeat(c, a, 1, 10, 10);
        heartbeat(c, b, 1, 10, 10);
        assertThat(profile(c).path("stats").path("totalSeconds").asDouble()).isEqualTo(10);
        TIME.advance(1);
        heartbeat(c, a, 2, 200, 100);
        assertThat(profile(c).path("stats").path("totalSeconds").asDouble()).isEqualTo(11);
    }

    @Test
    void midnightSplitsDailyTimeAndAverageIncludesInactiveNaturalDays() throws Exception {
        TIME.set("2026-10-25T15:59:50Z");
        Client c = client();
        String stream = start(c, "lesson1");
        TIME.advance(20);
        heartbeat(c, stream, 1, 20, 20);
        JsonNode twoDays = profile(c);
        assertThat(twoDays.path("stats").path("dailyAverageSeconds").asDouble()).isEqualTo(10);
        assertThat(twoDays.path("daily").path("2026-10-25").asDouble()).isEqualTo(10);
        assertThat(twoDays.path("daily").path("2026-10-26").asDouble()).isEqualTo(10);
        TIME.advance(48 * 3600);
        assertThat(profile(c).path("stats").path("dailyAverageSeconds").asDouble()).isEqualTo(5);
    }

    @Test
    void importsProgressWithoutFabricatingListeningAndCannotReplaceNewerServerProgress() throws Exception {
        TIME.set("2026-10-22T10:00:00Z");
        Client c = client();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("lessonId", "lesson1"); entry.put("position", 99);
        entry.put("updatedAt", TIME.millis() - 1000); entry.put("listenedSeconds", 99999);
        entry.put("completed", true);
        mvc.perform(post("/api/user/import").session(c.session).header("X-CSRF-Token", c.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Collections.singletonMap("records", Arrays.asList(entry)))))
                .andExpect(status().isNoContent());
        JsonNode imported = profile(c);
        assertThat(imported.path("stats").path("totalSeconds").asDouble()).isZero();
        assertThat(imported.path("stats").path("effectiveEpisodes").asInt()).isZero();
        assertThat(imported.path("records").get(0).path("position").asDouble()).isEqualTo(99);
        String stream = start(c, "lesson1");
        TIME.advance(10); heartbeat(c, stream, 1, 10, 15);
        mvc.perform(post("/api/user/import").session(c.session).header("X-CSRF-Token", c.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Collections.singletonMap("records", Arrays.asList(entry)))))
                .andExpect(status().isNoContent());
        assertThat(profile(c).path("records").get(0).path("position").asDouble()).isEqualTo(15);
    }

    @Test
    void pressingPlayAloneAddsARecordWithoutListeningTime() throws Exception {
        TIME.set("2026-10-28T10:00:00Z");
        Client c = client();
        start(c, "lesson2");
        JsonNode profile = profile(c);
        assertThat(profile.path("records")).hasSize(1);
        assertThat(profile.path("records").get(0).path("lessonId").asText()).isEqualTo("lesson2");
        assertThat(profile.path("records").get(0).path("listenedSeconds").asDouble()).isZero();
        assertThat(profile.path("records").get(0).path("lastListenedAt").asText()).isEqualTo("2026-10-28T10:00:00Z");
        assertThat(profile.path("stats").path("totalSeconds").asDouble()).isZero();
        assertThat(profile.path("stats").path("listenedEpisodes").asInt()).isZero();
    }

    @Test
    void importingAnOpenedButUnplayedLessonDoesNotCreateHistory() throws Exception {
        TIME.set("2026-10-28T11:00:00Z");
        Client c = client();
        Map<String, Object> opened = new LinkedHashMap<>();
        opened.put("lessonId", "lesson1"); opened.put("position", 0); opened.put("updatedAt", TIME.millis() - 1000);
        Map<String, Object> played = new LinkedHashMap<>();
        played.put("lessonId", "lesson2"); played.put("position", 0); played.put("updatedAt", 0);
        mvc.perform(post("/api/user/import").session(c.session).header("X-CSRF-Token", c.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        Collections.singletonMap("records", Arrays.asList(opened, played)))))
                .andExpect(status().isNoContent());
        JsonNode profile = profile(c);
        assertThat(profile.path("records")).hasSize(1);
        assertThat(profile.path("records").get(0).path("lessonId").asText()).isEqualTo("lesson2");
        assertThat(profile.path("stats").path("totalSeconds").asDouble()).isZero();
    }

    @Test
    void usersAreIsolatedAndInvalidInputsNeverCreateRecords() throws Exception {
        TIME.set("2026-10-23T10:00:00Z");
        mvc.perform(get("/api/user/profile")).andExpect(status().isUnauthorized());
        Client a = client(), b = client();
        String stream = start(a, "lesson1");
        TIME.advance(10); heartbeat(a, stream, 1, 10, 10);
        assertThat(profile(b).path("records").size()).isZero();
        mvc.perform(post("/api/user/listening").session(b.session).header("X-CSRF-Token", b.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(stream, 2, 20, 20)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/user/listening/start").session(a.session).header("X-CSRF-Token", a.csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lessonId\":\"../../users.json\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/user/listening").session(a.session).header("X-CSRF-Token", a.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(stream, 2, -1, 20)))
                .andExpect(status().isBadRequest());
        assertThat(profile(a).path("stats").path("totalSeconds").asDouble()).isEqualTo(10);
    }

    @Test
    void favoritesAreIdempotentNewestFirstAndIsolatedBetweenUsers() throws Exception {
        TIME.set("2026-10-27T10:00:00Z");
        Client a = client(), b = client();
        favorite(a, "lesson1");
        favorite(a, "lesson1");
        TIME.advance(1);
        favorite(a, "lesson2");
        JsonNode favorites = profile(a).path("favorites");
        assertThat(favorites).hasSize(2);
        assertThat(favorites.get(0).path("lessonId").asText()).isEqualTo("lesson2");
        assertThat(favorites.get(0).path("title").asText()).isEqualTo("Lesson 2");
        assertThat(favorites.get(0).path("duration").asDouble()).isEqualTo(100);
        assertThat(favorites.get(0).path("available").asBoolean()).isTrue();
        assertThat(favorites.get(0).path("favoritedAt").asText()).isEqualTo("2026-10-27T10:00:01Z");
        assertThat(profile(b).path("favorites")).isEmpty();

        unfavorite(a, "lesson1");
        assertThat(profile(a).path("favorites")).hasSize(1);
        unfavorite(a, "lesson1");
        assertThat(profile(a).path("favorites")).hasSize(1);
        assertThat(listening.load(a.id).favorites.get("lesson2")).isEqualTo(TIME.millis());
    }

    @Test
    void favoritesRequireLoginCsrfAndAnExistingLesson() throws Exception {
        TIME.set("2026-10-27T11:00:00Z");
        Client c = client();
        mvc.perform(put("/api/user/favorites/lesson1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/user/favorites/lesson1").session(c.session)).andExpect(status().isForbidden());
        mvc.perform(put("/api/user/favorites/missing").session(c.session).header("X-CSRF-Token", c.csrf))
                .andExpect(status().isNotFound());
        favorite(c, "lesson1");
        assertThat(profile(c).path("favorites").get(0).path("lessonId").asText()).isEqualTo("lesson1");
        unfavorite(c, "lesson1");
        assertThat(profile(c).path("favorites")).isEmpty();
    }

    @Test
    void profileSurvivesStoreReloadAndDeletedLessonsKeepTheirHistory() throws Exception {
        TIME.set("2026-10-24T10:00:00Z");
        Client c = client();
        String stream = start(c, "lesson3");
        TIME.advance(80); heartbeat(c, stream, 1, 80, 80);
        JsonNode first = profile(c);
        assertThat(first.path("records").get(0).path("title").asText()).isEqualTo("Lesson 3");
        assertThat(listening.load(c.id).records.get("lesson3").listenedSeconds).isEqualTo(80);
        jdbcTemplate.update("DELETE FROM lessons WHERE id = ?", "lesson3");
        JsonNode afterDeletion = profile(c);
        assertThat(afterDeletion.path("stats").path("effectiveEpisodes").asInt()).isEqualTo(1);
        assertThat(afterDeletion.path("records").get(0).path("title").asText()).isEqualTo("Lesson 3");
        assertThat(afterDeletion.path("records").get(0).path("available").asBoolean()).isFalse();
    }

    @Test
    void analysisRequiresTenDistinctEffectiveLessonsAndNeverFallsBackToTranslationKey() throws Exception {
        TIME.set("2026-10-26T10:00:00Z");
        Client c = client();
        for (int i = 1; i <= 9; i++) {
            String stream = start(c, "lesson" + i);
            TIME.advance(80); heartbeat(c, stream, 1, 80, 80);
        }
        mvc.perform(post("/api/user/analysis").session(c.session).header("X-CSRF-Token", c.csrf))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        String tenth = start(c, "lesson10");
        TIME.advance(80); heartbeat(c, tenth, 1, 80, 80);
        assertThat(profile(c).path("stats").path("analysisEligible").asBoolean()).isTrue();
        mvc.perform(post("/api/user/analysis").session(c.session).header("X-CSRF-Token", c.csrf))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    private Client client() throws Exception {
        MvcResult guest = mvc.perform(get("/api/user/session")).andExpect(status().isOk()).andReturn();
        MockHttpSession s = (MockHttpSession) guest.getRequest().getSession(false);
        String csrf = mapper.readTree(guest.getResponse().getContentAsString()).path("csrfToken").asText();
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("username", "listen_" + UUID.randomUUID().toString().substring(0, 8));
        credentials.put("password", "test-password-123");
        MvcResult registered = mvc.perform(post("/api/user/register").session(s).header("X-CSRF-Token", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(credentials)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = mapper.readTree(registered.getResponse().getContentAsString());
        return new Client(s, body.path("csrfToken").asText(), body.path("user").path("id").asText());
    }
    private String start(Client c, String lessonId) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/listening/start").session(c.session)
                .header("X-CSRF-Token", c.csrf).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Collections.singletonMap("lessonId", lessonId))))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("streamId").asText();
    }
    private void heartbeat(Client c, String stream, int sequence, double seconds, double position) throws Exception {
        mvc.perform(post("/api/user/listening").session(c.session).header("X-CSRF-Token", c.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(heartbeatBody(stream, sequence, seconds, position)))
                .andExpect(status().isNoContent());
    }
    private String heartbeatBody(String stream, int sequence, double seconds, double position) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("streamId", stream); body.put("sequence", sequence); body.put("seconds", seconds); body.put("position", position);
        return mapper.writeValueAsString(body);
    }
    private void favorite(Client c, String lessonId) throws Exception {
        mvc.perform(put("/api/user/favorites/" + lessonId).session(c.session).header("X-CSRF-Token", c.csrf))
                .andExpect(status().isNoContent());
    }
    private void unfavorite(Client c, String lessonId) throws Exception {
        mvc.perform(delete("/api/user/favorites/" + lessonId).session(c.session).header("X-CSRF-Token", c.csrf))
                .andExpect(status().isNoContent());
    }
    private JsonNode profile(Client c) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/user/profile").session(c.session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private static Path createRoot() {
        try {
            Path root = Files.createTempDirectory("cadence-listening-");
            List<Map<String, Object>> lessons = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                Map<String, Object> lesson = new LinkedHashMap<>();
                lesson.put("id", "lesson" + i); lesson.put("title", "Lesson " + i);
                lesson.put("category", i % 2 == 0 ? "Culture" : "Science"); lesson.put("duration", 100);
                lessons.add(lesson);
            }
            new ObjectMapper().writeValue(root.resolve("lessons.json").toFile(), lessons);
            return root;
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private static class Client {
        final MockHttpSession session; final String csrf; final String id;
        Client(MockHttpSession session, String csrf, String id) { this.session = session; this.csrf = csrf; this.id = id; }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T00:00:00Z"));
        void set(String value) { now.set(Instant.parse(value)); }
        void advance(long seconds) { now.updateAndGet(i -> i.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
