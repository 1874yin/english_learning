package com.xiaojie.englishlearning.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"app.translation.enabled=false", "app.user-analysis.api-key=analysis-test-only", "app.user-analysis.model=analysis-test-model"})
@AutoConfigureMockMvc
class UserAnalysisApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicLong TIME = new AtomicLong(Instant.parse("2026-10-20T10:00:00Z").toEpochMilli());
    private static final AtomicReference<String> REQUEST = new AtomicReference<>();
    private static final AtomicReference<String> AUTH = new AtomicReference<>();
    private static final AtomicBoolean MALFORMED = new AtomicBoolean();
    private static final HttpServer SERVER = server();
    private static final Path ROOT = root();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
        registry.add("app.user-analysis.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock analysisTestClock() {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return Instant.ofEpochMilli(TIME.get()); }
            };
        }
    }
    @Autowired MockMvc mvc;
    @AfterAll static void stop() { SERVER.stop(0); }

    @Test
    void usesDedicatedCredentialsSendsOnlyAggregatesAndKeepsLastGoodAnalysisOnBadResponse() throws Exception {
        MvcResult guest = mvc.perform(get("/api/user/session")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) guest.getRequest().getSession(false);
        String csrf = JSON.readTree(guest.getResponse().getContentAsString()).path("csrfToken").asText();
        MvcResult reg = mvc.perform(post("/api/user/register").session(session).header("X-CSRF-Token", csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"private-learner\",\"password\":\"secret-test-password\"}"))
                .andExpect(status().isCreated()).andReturn();
        csrf = JSON.readTree(reg.getResponse().getContentAsString()).path("csrfToken").asText();
        String userId = JSON.readTree(reg.getResponse().getContentAsString()).path("user").path("id").asText();
        for (int i = 1; i <= 10; i++) {
            MvcResult start = mvc.perform(post("/api/user/listening/start").session(session).header("X-CSRF-Token", csrf)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"lessonId\":\"lesson" + i + "\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String stream = JSON.readTree(start.getResponse().getContentAsString()).path("streamId").asText();
            TIME.addAndGet(80000);
            mvc.perform(post("/api/user/listening").session(session).header("X-CSRF-Token", csrf)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"streamId\":\"" + stream + "\",\"sequence\":1,\"seconds\":80,\"position\":80}"))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/user/profile").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisConfigured").value(true));
        mvc.perform(post("/api/user/analysis").session(session).header("X-CSRF-Token", csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("稳步积累的学习者"))
                .andExpect(jsonPath("$.basedOnEpisodes").value(10)).andExpect(jsonPath("$.generatedAt").isNotEmpty());
        assertThat(AUTH.get()).isEqualTo("Bearer analysis-test-only");
        assertThat(REQUEST.get()).doesNotContain("private-learner", "secret-test-password", userId, "Private lesson title");
        JsonNode sent = JSON.readTree(REQUEST.get());
        assertThat(sent.path("model").asText()).isEqualTo("analysis-test-model");
        JsonNode summary = JSON.readTree(sent.path("messages").get(1).path("content").asText());
        assertThat(summary.path("stats").path("totalSeconds").asDouble()).isEqualTo(800);
        mvc.perform(get("/api/user/profile").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.title").value("稳步积累的学习者"));
        MALFORMED.set(true); TIME.addAndGet(61000);
        mvc.perform(post("/api/user/analysis").session(session).header("X-CSRF-Token", csrf))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/user/profile").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.title").value("稳步积累的学习者"));
    }

    private static HttpServer server() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/chat/completions", exchange -> {
                try (InputStream in = exchange.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024]; int read;
                    while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                    REQUEST.set(new String(out.toByteArray(), StandardCharsets.UTF_8));
                }
                AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String content = MALFORMED.get() ? "{\"title\":\"incomplete\"}" : "{\"title\":\"稳步积累的学习者\",\"interests\":\"偏好科学内容。\",\"habits\":\"持续收听。\",\"investment\":\"累计投入了学习时间。\",\"suggestions\":[\"保持聆听。\",\"重听并跟读。\"],\"encouragement\":\"每一天都在进步。\"}";
                byte[] body = JSON.writeValueAsBytes(Collections.singletonMap("choices", Collections.singletonList(
                        Collections.singletonMap("message", Collections.singletonMap("content", content)))));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            s.start(); return s;
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private static Path root() {
        try {
            Path root = Files.createTempDirectory("cadence-analysis-");
            List<Map<String, Object>> lessons = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                Map<String, Object> lesson = new LinkedHashMap<>();
                lesson.put("id", "lesson" + i); lesson.put("title", "Private lesson title " + i);
                lesson.put("category", "Science"); lesson.put("duration", 100); lessons.add(lesson);
            }
            JSON.writeValue(root.resolve("lessons.json").toFile(), lessons); return root;
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
}
