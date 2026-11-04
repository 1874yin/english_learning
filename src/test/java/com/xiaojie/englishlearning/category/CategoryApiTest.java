package com.xiaojie.englishlearning.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"app.translation.enabled=false", "spring.flyway.enabled=true"})
@AutoConfigureMockMvc
class CategoryApiTest extends DatabaseTestContainers {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
        registry.add("app.admin.password", () -> "test-password");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM lessons");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("UPDATE lesson_number_sequence SET next_number = 1, next_sort_order = 1 WHERE id = 1");
    }

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
    void adminCanManageCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Daily Life")));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Travel\"}")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  travel  \"}")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasItem("travel")));

        // Case-insensitive duplicates keep the stored spelling.
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Travel\"}")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", not(hasItem("Travel"))));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("travel")));

        mockMvc.perform(delete("/api/admin/categories").param("name", "travel").session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("travel"))));

        // Categories used by a lesson stay visible in the public list and cannot
        // be removed from the suggestion list.
        mockMvc.perform(multipart("/api/admin/lessons")
                        .file(audioFixture())
                        .param("title", "A Travel Test")
                        .param("category", "Travel")
                        .param("level", "Intermediate")
                        .param("transcriptText", "One sentence. Another sentence.")
                        .session(session))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Travel")));

        mockMvc.perform(delete("/api/admin/categories").param("name", "Travel").session(session))
                .andExpect(status().isConflict());
    }

    @Test
    void browseCategoriesCountAllLessonsAndFilterByLevel() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Browse Test\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empty Browse Test\"}"))
                .andExpect(status().isCreated());

        String[] categories = { "browse test", "Browse   Test", "BROWSE TEST", "Custom Browse Test" };
        String[] levels = { "Beginner", "beginner", "Advanced", "Intermediate" };
        for (int i = 0; i < categories.length; i += 1) {
            mockMvc.perform(multipart("/api/admin/lessons")
                            .file(audioFixture())
                            .param("title", "Browse lesson " + i)
                            .param("category", categories[i])
                            .param("level", levels[i])
                            .param("transcriptText", "One sentence. Another sentence."))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/categories/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Browse Test')].count", hasItem(3)))
                .andExpect(jsonPath("$[?(@.name == 'Custom Browse Test')].count", hasItem(1)))
                .andExpect(jsonPath("$[*].name", not(hasItem("Empty Browse Test"))))
                .andExpect(jsonPath("$..transcript").isEmpty())
                .andExpect(jsonPath("$..audio").isEmpty());

        mockMvc.perform(get("/api/categories/browse").param("level", " beginner "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Browse Test"))
                .andExpect(jsonPath("$[0].count").value(2));

        mockMvc.perform(get("/api/categories/browse").param("level", "Advanced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Browse Test"))
                .andExpect(jsonPath("$[0].count").value(1));

        mockMvc.perform(get("/api/categories/browse").param("level", "Missing Level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Browse Test")))
                .andExpect(jsonPath("$", hasItem("Empty Browse Test")));
    }

    @Test
    void blankCategoryIsRejected() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}")
                        .session(session))
                .andExpect(status().isBadRequest());
    }

    private static MockMultipartFile audioFixture() throws IOException {
        ClassPathResource resource = new ClassPathResource("fixtures/sample.mp3");
        return new MockMultipartFile(
                "audio",
                "sample.mp3",
                "audio/mpeg",
                resource.getInputStream());
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("cadence-category-test-");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test storage directory", ex);
        }
    }
}
