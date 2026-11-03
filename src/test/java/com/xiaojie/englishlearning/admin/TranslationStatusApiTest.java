package com.xiaojie.englishlearning.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.translation.enabled=false")
@AutoConfigureMockMvc
class TranslationStatusApiTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired
    private MockMvc mockMvc;

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
    void localAdminRequestsCanReadTheTranslationStatus() throws Exception {
        mockMvc.perform(get("/api/admin/translation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.provider").value("deepseek"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.stateCurrent").value(false))
                .andExpect(jsonPath("$.totalLessons").value(0))
                .andExpect(jsonPath("$.totalLines").value(0))
                .andExpect(jsonPath("$.translatedLines").value(0))
                .andExpect(jsonPath("$.missingLines").value(0))
                .andExpect(jsonPath("$.translatedLessons").value(0))
                .andExpect(jsonPath("$.missingLessons").value(0));
    }

    @Test
    void remoteRequestsNeedAnAdminSession() throws Exception {
        mockMvc.perform(get("/api/admin/translation/status")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("cadence-status-test-");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test storage directory", ex);
        }
    }
}
