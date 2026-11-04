package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonStore;
import com.xiaojie.englishlearning.migration.JsonDataImportRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(properties = {
        "app.persistence.mode=json-read-only",
        "app.translation.enabled=false",
        "app.user-analysis.api-key=",
        "app.admin.password=test-password"
})
@AutoConfigureMockMvc
class JsonReadOnlyRollbackIntegrationTest {
    private static final Path ROOT = createSnapshot();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:1/unavailable");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private LessonPersistence lessons;
    @Autowired private CategoryPersistence categories;
    @Autowired private UserPersistence users;
    @Autowired private ListeningPersistence listening;
    @Autowired private JsonDataImportRunner importer;

    @Test
    void servesOriginalLessonCategoryAccountAndListeningSnapshot() throws Exception {
        assertThat(lessons.findAll()).hasSize(1);
        assertThat(categories.findAll()).contains("Science");
        assertThat(users.findById("import-user").username).isEqualTo("learner");
        assertThat(listening.load("import-user").records).containsKey("lesson-7");

        mvc.perform(get("/api/lessons"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("lesson-7"));
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("Science")));
        MockHttpSession userSession = new MockHttpSession();
        userSession.setAttribute("cadence.user.id", "import-user");
        userSession.setAttribute("cadence.user.csrf", "snapshot-csrf");
        mvc.perform(get("/api/user/profile").session(userSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].title").value("Imported lesson"));
    }

    @Test
    void rejectsMutationsWithoutChangingAnySnapshotFile() throws Exception {
        Map<String, String> before = snapshotHashes(ROOT);
        MvcResult login = mvc.perform(post("/api/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-password\"}"))
                .andExpect(status().isNoContent()).andReturn();
        MockHttpSession adminSession = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/admin/categories").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New category\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/api/admin/categories").session(adminSession).param("name", "Science"))
                .andExpect(status().isServiceUnavailable());

        MockMultipartFile audio = new MockMultipartFile("audio", "sample.mp3", "audio/mpeg",
                new ClassPathResource("fixtures/sample.mp3").getInputStream());
        mvc.perform(multipart("/api/admin/lessons").file(audio).session(adminSession)
                        .param("title", "Should not persist").param("category", "Science")
                        .param("level", "Beginner").param("transcriptText", "A sentence."))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(multipart("/api/admin/lessons/lesson-7").file(audio).session(adminSession)
                        .param("title", "Should not update").param("category", "Science")
                        .param("level", "Beginner").param("transcriptText", "A sentence.")
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isServiceUnavailable());

        MockHttpSession userSession = new MockHttpSession();
        userSession.setAttribute("cadence.user.id", "import-user");
        userSession.setAttribute("cadence.user.csrf", "snapshot-csrf");
        mvc.perform(post("/api/user/register").session(userSession).header("X-CSRF-Token", "snapshot-csrf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-learner\",\"password\":\"long-test-password\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(put("/api/user/favorites/lesson-7").session(userSession)
                        .header("X-CSRF-Token", "snapshot-csrf"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/api/user/favorites/lesson-7").session(userSession)
                        .header("X-CSRF-Token", "snapshot-csrf"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/user/listening/start").session(userSession).header("X-CSRF-Token", "snapshot-csrf")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lessonId\":\"lesson-7\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/user/listening").session(userSession).header("X-CSRF-Token", "snapshot-csrf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamId\":\"snapshot-stream\",\"sequence\":1,\"seconds\":10,\"position\":10}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/user/import").session(userSession).header("X-CSRF-Token", "snapshot-csrf")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"records\":[]}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/user/analysis").session(userSession).header("X-CSRF-Token", "snapshot-csrf"))
                .andExpect(status().isServiceUnavailable());

        assertThat(snapshotHashes(ROOT)).isEqualTo(before);
    }

    @Test
    void databaseAdapterFailureIsSurfacedInsteadOfLoadingJsonSnapshot() throws Exception {
        LessonStore store = new LessonStore(new com.xiaojie.englishlearning.storage.StoragePaths(ROOT.toString()), mapper,
                new FailingLessonPersistence());

        assertThatThrownBy(store::findAll).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mysql unavailable");
    }

    @Test
    void jsonReadOnlyModeCannotStartTheOneTimeDatabaseImporter() {
        assertThatThrownBy(() -> importer.execute(ROOT, ROOT.resolveSibling("unused-import-backup"), true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.persistence.mode=database");
    }

    private static Path createSnapshot() {
        try {
            Path source = Path.of(JsonReadOnlyRollbackIntegrationTest.class.getClassLoader()
                    .getResource("migration").toURI());
            Path root = Files.createTempDirectory("cadence-json-read-only-");
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    Path destination = root.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(destination);
                    else Files.copy(path, destination);
                }
            }
            return root;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, String> snapshotHashes(Path root) throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                hashes.put(root.relativize(file).toString().replace('\\', '/'),
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
        }
        return hashes;
    }

    private static final class FailingLessonPersistence implements LessonPersistence {
        @Override public List<Lesson> findAll() { throw new IllegalStateException("mysql unavailable"); }
        @Override public java.util.Optional<Lesson> findById(String id) { throw new IllegalStateException("mysql unavailable"); }
        @Override public String nextNumber() { throw new IllegalStateException("mysql unavailable"); }
        @Override public Lesson insert(Lesson lesson) { throw new IllegalStateException("mysql unavailable"); }
        @Override public Lesson replace(Lesson lesson) { throw new IllegalStateException("mysql unavailable"); }
        @Override public boolean updateTranslations(String id, List<String> expected, List<String> translations) {
            throw new IllegalStateException("mysql unavailable");
        }
        @Override public int clearAllTranslations() { throw new IllegalStateException("mysql unavailable"); }
        @Override public com.xiaojie.englishlearning.lesson.LessonPage findPage(int page, int size) {
            throw new IllegalStateException("mysql unavailable");
        }
    }
}
