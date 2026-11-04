package com.xiaojie.englishlearning.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaojie.englishlearning.persistence.mongodb.ListeningMongoRepository;
import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import com.xiaojie.englishlearning.user.ListeningData;
import com.xiaojie.englishlearning.user.UserStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"app.translation.enabled=false", "app.user-analysis.enabled=false", "spring.flyway.enabled=true"})
class JsonDataImportRunnerTest extends DatabaseTestContainers {

    @Autowired
    private JsonDataImportRunner importer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ListeningMongoRepository listeningMongoRepository;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void clearTargets() {
        listeningMongoRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM data_import_manifest");
        jdbcTemplate.update("DELETE FROM lessons");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("UPDATE lesson_number_sequence SET next_number = 1, next_sort_order = 1 WHERE id = 1");
    }

    @Test
    void validDryRunReportsHashesAndCountsWithoutWritingTargets() throws Exception {
        Path root = createFixture();

        ImportManifest report = importer.preflight(root);

        assertThat(report.getStatus()).isEqualTo("DRY_RUN");
        assertThat(report.getSourceCounts()).containsEntry("lessons", 1).containsEntry("categories", 2)
                .containsEntry("users", 1).containsEntry("userData", 1);
        assertThat(report.getSourceHashes().keySet()).containsExactlyInAnyOrder("lessons.json", "categories.json",
                "users.json", "user-data/import-user.json");
        assertThat(report.getSourceHashes().values()).allMatch(hash -> hash.matches("[a-f0-9]{64}"));
        assertThat(report.getManifestId()).matches("[a-f0-9]{64}");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(listeningMongoRepository.count()).isZero();
    }

    @Test
    void malformedAndDuplicateSourcesFailPreflightBeforeAnyTargetWrite() throws Exception {
        Path root = createFixture();
        Files.writeString(root.resolve("lessons.json"), "not-json");
        Path malformedRoot = root;

        assertThatThrownBy(() -> importer.preflight(malformedRoot)).isInstanceOf(ImportValidationException.class);
        assertEmptyTargets();

        root = createFixture();
        Map<String, Object> duplicate = lesson("lesson-7", "07");
        mapper.writeValue(root.resolve("lessons.json").toFile(), List.of(duplicate, duplicate));
        Path duplicateRoot = root;
        assertThatThrownBy(() -> importer.preflight(duplicateRoot))
                .isInstanceOf(ImportValidationException.class).hasMessageContaining("duplicate lesson id");
        assertEmptyTargets();

        root = createFixture();
        UserStore.Account first = mapper.readValue(root.resolve("users.json").toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<List<UserStore.Account>>() { }).get(0);
        first.username = "same-name";
        UserStore.Account second = new UserStore.Account();
        second.id = "import-user-2";
        second.username = "SAME-NAME";
        second.passwordHash = first.passwordHash;
        second.createdAt = first.createdAt;
        mapper.writeValue(root.resolve("users.json").toFile(), List.of(first, second));
        Path duplicateUsersRoot = root;
        assertThatThrownBy(() -> importer.preflight(duplicateUsersRoot))
                .isInstanceOf(ImportValidationException.class).hasMessageContaining("duplicate username");
        assertEmptyTargets();
    }

    @Test
    void missingOrBlankLessonTimestampFailsPreflightBeforeAnyTargetWrite() throws Exception {
        for (String timestamp : new String[] {null, ""}) {
            Path root = createFixture();
            List<Map<String, Object>> sourceLessons = mapper.readValue(root.resolve("lessons.json").toFile(),
                    new TypeReference<List<Map<String, Object>>>() { });
            sourceLessons.get(0).put("updatedAt", timestamp);
            mapper.writeValue(root.resolve("lessons.json").toFile(), sourceLessons);

            assertThatThrownBy(() -> importer.preflight(root)).isInstanceOf(ImportValidationException.class)
                    .hasMessageContaining("updatedAt");
            assertEmptyTargets();
        }
    }

    @Test
    void missingOrCorruptPerUserFilesFailPreflight() throws Exception {
        Path root = createFixture();
        Files.delete(root.resolve("user-data/import-user.json"));
        Path missingRoot = root;
        assertThatThrownBy(() -> importer.preflight(missingRoot)).isInstanceOf(ImportValidationException.class)
                .hasMessageContaining("missing listening data");
        assertEmptyTargets();

        root = createFixture();
        Files.writeString(root.resolve("user-data/import-user.json"), "{");
        Path corruptRoot = root;
        assertThatThrownBy(() -> importer.preflight(corruptRoot)).isInstanceOf(ImportValidationException.class)
                .hasMessageContaining("import-user");
        assertEmptyTargets();
    }

    @Test
    void oversizedUserDocumentFailsPreflightWithUserId() throws Exception {
        Path root = createFixture();
        ListeningData oversized = new ListeningData();
        oversized.analysis = mapper.createObjectNode().put("payload", "x".repeat(16 * 1024 * 1024));
        mapper.writeValue(root.resolve("user-data/import-user.json").toFile(), oversized);

        assertThatThrownBy(() -> importer.preflight(root)).isInstanceOf(ImportValidationException.class)
                .hasMessageContaining("import-user").hasMessageContaining("16 MiB");
        assertEmptyTargets();
    }

    @Test
    void executeRequiresConfirmedMatchingBackupAndRejectsNonEmptyTargets() throws Exception {
        Path root = createFixture();
        Path backup = copyTree(root, root.resolveSibling("backup-" + UUID.randomUUID()));
        assertThatThrownBy(() -> importer.execute(root, backup, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("confirm");
        Path incompleteBackup = copyTree(root, root.resolveSibling("incomplete-backup-" + UUID.randomUUID()));
        Files.delete(incompleteBackup.resolve("users.json"));
        assertThatThrownBy(() -> importer.execute(root, incompleteBackup, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("complete");
        assertEmptyTargets();

        jdbcTemplate.update("INSERT INTO categories (name_key, name) VALUES ('existing', 'Existing')");
        assertThatThrownBy(() -> importer.execute(root, backup, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not empty");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(listeningMongoRepository.count()).isZero();
    }

    @Test
    void executeVerifiesImportedContentAndMatchingManifestRetryDoesNotDuplicate() throws Exception {
        Path root = createFixture();
        Path backup = copyTree(root, root.resolveSibling("backup-" + UUID.randomUUID()));
        byte[] originalLessons = Files.readAllBytes(root.resolve("lessons.json"));
        byte[] originalCategories = Files.readAllBytes(root.resolve("categories.json"));
        byte[] originalUsers = Files.readAllBytes(root.resolve("users.json"));
        byte[] originalListening = Files.readAllBytes(root.resolve("user-data/import-user.json"));

        ImportManifest completed = importer.execute(root, backup, true);
        assertThat(completed.getStatus()).isEqualTo("COMPLETE");
        assertThat(completed.getDestinationCounts()).containsEntry("lessons", 1).containsEntry("categories", 2)
                .containsEntry("users", 1).containsEntry("userData", 1);
        assertThat(jdbcTemplate.queryForObject("SELECT number FROM lessons WHERE id = 'lesson-7'", String.class))
                .isEqualTo("07");
        assertThat(jdbcTemplate.queryForObject("SELECT next_number FROM lesson_number_sequence WHERE id = 1", Integer.class))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = 'import-user'", String.class))
                .isEqualTo(mapper.readTree(root.resolve("users.json").toFile()).get(0).path("passwordHash").asText());
        assertThat(listeningMongoRepository.findById("import-user")).isPresent();

        ImportManifest retry = importer.execute(root, backup, true);
        assertThat(retry.getManifestId()).isEqualTo(completed.getManifestId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(listeningMongoRepository.count()).isEqualTo(1);
        assertThat(Files.readAllBytes(root.resolve("lessons.json"))).containsExactly(originalLessons);
        assertThat(Files.readAllBytes(root.resolve("categories.json"))).containsExactly(originalCategories);
        assertThat(Files.readAllBytes(root.resolve("users.json"))).containsExactly(originalUsers);
        assertThat(Files.readAllBytes(root.resolve("user-data/import-user.json"))).containsExactly(originalListening);

        Map<String, Object> changedLesson = lesson("lesson-7", "07");
        changedLesson.put("title", "Changed after initial import");
        mapper.writeValue(root.resolve("lessons.json").toFile(), List.of(changedLesson));
        Path changedBackup = copyTree(root, root.resolveSibling("changed-backup-" + UUID.randomUUID()));
        assertThatThrownBy(() -> importer.execute(root, changedBackup, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("manifest history");
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM lessons WHERE id = 'lesson-7'", String.class))
                .isEqualTo("Imported lesson");
    }

    private void assertEmptyTargets() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM data_import_manifest", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(listeningMongoRepository.count()).isZero();
    }

    private Path createFixture() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader().getResource("migration").toURI());
        return copyTree(fixture, Files.createTempDirectory("cadence-import-" + UUID.randomUUID()));
    }


    private Map<String, Object> lesson(String id, String number) {
        Map<String, Object> lesson = new LinkedHashMap<>();
        lesson.put("id", id);
        lesson.put("number", number);
        lesson.put("title", "Imported lesson");
        lesson.put("titleZh", null);
        lesson.put("category", "Science");
        lesson.put("level", "Beginner");
        lesson.put("duration", 42.5);
        lesson.put("audio", "/media/import.mp3");
        lesson.put("updatedAt", "2026-11-03T00:00:00Z");
        lesson.put("transcript", List.of(Map.of("start", 0, "end", 42.5, "text", "Hello", "translation", "你好")));
        lesson.put("keyPhrases", List.of(Map.of("phrase", "hello", "meaning", "你好")));
        return lesson;
    }

    private Path copyTree(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination);
            }
        }
        return target;
    }
}
