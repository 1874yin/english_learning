package com.xiaojie.englishlearning.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.translation.enabled=false")
@AutoConfigureMockMvc
class LessonApiTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
        registry.add("app.admin.password", () -> "test-password");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    static void cleanStorage() throws IOException {
        if (STORAGE_ROOT == null || !Files.exists(STORAGE_ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(STORAGE_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    /* Best-effort cleanup of the temporary test directory. */
                }
            });
        }
    }

    @Test
    void adminCanCreateAndUpdateLesson() throws Exception {
        mockMvc.perform(get("/api/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));

        MockMultipartFile audio = audioFixture();
        mockMvc.perform(multipart("/api/admin/lessons")
                        .file(audio)
                        .param("title", "No session")
                        .param("transcriptText", "This upload should be rejected.")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/admin/lessons").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        String originalTranscript = "Dr. Smith wakes at seven. The room is quiet.\n\n"
                + "She reads for ten minutes, then starts work.";
        MvcResult created = mockMvc.perform(multipart("/api/admin/lessons")
                        .file(audio)
                        .param("title", "A Quiet Test Morning")
                        .param("titleZh", "测试早晨")
                        .param("category", "Daily Life")
                        .param("level", "Beginner")
                        .param("summary", "A short test lesson.")
                        .param("summaryZh", "一节测试课程。")
                        .param("transcriptText", originalTranscript)
                        .param("wordsText", "quiet /ˈkwaɪət/ adj. 安静的 — The room is quiet.\n"
                                + "minute 分钟")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.number").value("01"))
                .andExpect(jsonPath("$.audio", startsWith("/media/")))
                .andExpect(jsonPath("$.cover").value("/images/cover-default.svg"))
                .andExpect(jsonPath("$.duration").value(org.hamcrest.Matchers.greaterThan(40.0)))
                .andExpect(jsonPath("$.transcript", hasSize(3)))
                .andExpect(jsonPath("$.wordsText").value(
                        "quiet /ˈkwaɪət/ adj. 安静的 — The room is quiet.\nminute 分钟"))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = createdJson.get("id").asText();
        String audioUrl = createdJson.get("audio").asText();
        double duration = createdJson.get("duration").asDouble();
        double firstSentenceStart = createdJson.get("transcript").get(0).get("start").asDouble();

        mockMvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("audio/mpeg")));

        // The list endpoint returns playlist-sized summaries only: no
        // transcript, words or audio URL. The detail endpoint below carries
        // the full lesson.
        mockMvc.perform(get("/api/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].title").value("A Quiet Test Morning"))
                .andExpect(jsonPath("$.items[0].cover").value("/images/cover-default.svg"))
                .andExpect(jsonPath("$.items[0].transcript").doesNotExist())
                .andExpect(jsonPath("$.items[0].audio").doesNotExist())
                .andExpect(jsonPath("$.items[0].wordsText").doesNotExist())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/lessons").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(1));

        // Metadata-only edits keep the existing audio and estimated timeline.
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/admin/lessons/" + id)
                        .param("title", "Metadata Only Edit")
                        .param("transcriptText", originalTranscript)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Metadata Only Edit"))
                .andExpect(jsonPath("$.audio").value(audioUrl))
                .andExpect(jsonPath("$.cover").value("/images/cover-default.svg"))
                .andExpect(jsonPath("$.transcript[0].start").value(firstSentenceStart))
                .andExpect(jsonPath("$.wordsText").value(
                        "quiet /ˈkwaɪət/ adj. 安静的 — The room is quiet.\nminute 分钟"));

        MvcResult updated = mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                                "/api/admin/lessons/" + id)
                        .file(coverFixture())
                        .param("title", "Updated Test Lesson")
                        .param("category", "Culture")
                        .param("level", "Intermediate")
                        .param("transcriptText", "One new sentence. A second new sentence.")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Test Lesson"))
                .andExpect(jsonPath("$.audio").value(audioUrl))
                .andExpect(jsonPath("$.cover", startsWith("/covers/")))
                .andExpect(jsonPath("$.transcript", hasSize(2)))
                .andExpect(jsonPath("$.duration").value(duration))
                .andReturn();

        String coverUrl = objectMapper.readTree(updated.getResponse().getContentAsString())
                .get("cover").asText();
        mockMvc.perform(get(coverUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));

        mockMvc.perform(get("/api/admin/lessons").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sentenceCount").value(2))
                .andExpect(jsonPath("$[0].title").value("Updated Test Lesson"))
                .andExpect(jsonPath("$[0].cover").value(coverUrl));

        mockMvc.perform(get("/api/lessons/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Test Lesson"))
                .andExpect(jsonPath("$.cover").value(coverUrl))
                .andExpect(jsonPath("$.transcript[0].text").value("One new sentence."));

        MvcResult avifUpdated = mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                                "/api/admin/lessons/" + id)
                        .file(avifCoverFixture())
                        .param("title", "Updated Test Lesson")
                        .param("transcriptText", "One new sentence. A second new sentence.")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover", startsWith("/covers/")))
                .andReturn();

        String avifCoverUrl = objectMapper.readTree(avifUpdated.getResponse().getContentAsString())
                .get("cover").asText();
        assertThat(avifCoverUrl).isNotEqualTo(coverUrl);
        mockMvc.perform(get(avifCoverUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("image/avif")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get(coverUrl))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lessons/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover").value(avifCoverUrl));

        mockMvc.perform(get("/api/lessons/does-not-exist"))
                .andExpect(status().isNotFound());

        // A submitted blank value clears the plain-text word section.
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                                "/api/admin/lessons/" + id)
                        .param("title", "Updated Test Lesson")
                        .param("transcriptText", "One new sentence. A second new sentence.")
                        .param("wordsText", "")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wordsText").doesNotExist());

        mockMvc.perform(delete("/api/admin/logout").session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/lessons").session(session)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void localRequestsCanUseAdminApiWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/admin/lessons"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Admin-Local-Access", "true"));
    }

    @Test
    void adminPageIsServed() throws Exception {
        mockMvc.perform(get("/admin.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("text/html")));

        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin.html"));

        mockMvc.perform(get("/js/admin.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("javascript")));
    }

    private static MockMultipartFile audioFixture() throws IOException {
        ClassPathResource resource = new ClassPathResource("fixtures/sample.mp3");
        return new MockMultipartFile(
                "audio",
                "sample.mp3",
                "audio/mpeg",
                resource.getInputStream());
    }

    private static MockMultipartFile coverFixture() throws IOException {
        BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(23, 61, 52));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "cover",
                "cover.png",
                "image/png",
                output.toByteArray());
    }

    private static MockMultipartFile avifCoverFixture() {
        ByteBuffer buffer = ByteBuffer.allocate(48);
        buffer.putInt(28)
                .put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put("avif".getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put("avif".getBytes(StandardCharsets.US_ASCII))
                .put("mif1".getBytes(StandardCharsets.US_ASCII))
                .put("miaf".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(20)
                .put("ispe".getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .putInt(8)
                .putInt(6);
        return new MockMultipartFile(
                "cover",
                "cover.avif",
                "image/avif",
                buffer.array());
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("cadence-test-");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test storage directory", ex);
        }
    }
}
