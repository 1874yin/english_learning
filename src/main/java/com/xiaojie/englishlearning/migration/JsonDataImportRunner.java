package com.xiaojie.englishlearning.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.lesson.KeyPhrase;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.TranscriptLine;
import com.xiaojie.englishlearning.persistence.ListeningPersistence;
import com.xiaojie.englishlearning.persistence.LessonPersistence;
import com.xiaojie.englishlearning.persistence.UserPersistence;
import com.xiaojie.englishlearning.persistence.mongodb.ListeningDocumentSizeValidator;
import com.xiaojie.englishlearning.persistence.mysql.CategoryEntity;
import com.xiaojie.englishlearning.persistence.mysql.LessonEntity;
import com.xiaojie.englishlearning.persistence.mysql.LessonNumberSequenceEntity;
import com.xiaojie.englishlearning.persistence.mysql.mapper.CategoryMapper;
import com.xiaojie.englishlearning.persistence.mysql.mapper.LessonMapper;
import com.xiaojie.englishlearning.persistence.mysql.mapper.LessonNumberSequenceMapper;
import com.xiaojie.englishlearning.storage.StoragePaths;
import com.xiaojie.englishlearning.user.ListeningData;
import com.xiaojie.englishlearning.user.UserStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Component
public class JsonDataImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(JsonDataImportRunner.class);
    private static final TypeReference<List<Lesson>> LESSON_LIST = new TypeReference<>() { };
    private static final TypeReference<List<String>> CATEGORY_LIST = new TypeReference<>() { };
    private static final TypeReference<List<UserStore.Account>> USER_LIST = new TypeReference<>() { };
    private static final int MAX_CATEGORY_LENGTH = 60;
    private static final int MAX_USERNAME_LENGTH = 30;

    private final ObjectMapper mapper;
    private final LessonMapper lessonMapper;
    private final CategoryMapper categoryMapper;
    private final LessonNumberSequenceMapper sequenceMapper;
    private final UserPersistence users;
    private final LessonPersistence lessonPersistence;
    private final ListeningPersistence listening;
    private final ListeningDocumentSizeValidator sizeValidator;
    private final com.xiaojie.englishlearning.persistence.mongodb.ListeningMongoRepository listeningRepository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final StoragePaths storagePaths;
    private final String mode;
    private final String persistenceMode;
    private final String backupDirectory;
    private final boolean confirmed;

    public JsonDataImportRunner(ObjectMapper mapper, LessonMapper lessonMapper, CategoryMapper categoryMapper,
            LessonNumberSequenceMapper sequenceMapper, UserPersistence users, LessonPersistence lessonPersistence,
            ListeningPersistence listening, ListeningDocumentSizeValidator sizeValidator,
            com.xiaojie.englishlearning.persistence.mongodb.ListeningMongoRepository listeningRepository,
            JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager transactionManager,
            StoragePaths storagePaths,
            @Value("${app.data-import.mode:disabled}") String mode,
            @Value("${app.data-import.backup-dir:}") String backupDirectory,
            @Value("${app.data-import.confirm:false}") boolean confirmed,
            @Value("${app.persistence.mode:database}") String persistenceMode) {
        this.mapper = mapper;
        this.lessonMapper = lessonMapper;
        this.categoryMapper = categoryMapper;
        this.sequenceMapper = sequenceMapper;
        this.users = users;
        this.lessonPersistence = lessonPersistence;
        this.listening = listening;
        this.sizeValidator = sizeValidator;
        this.listeningRepository = listeningRepository;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.storagePaths = storagePaths;
        this.mode = mode;
        this.persistenceMode = persistenceMode;
        this.backupDirectory = backupDirectory;
        this.confirmed = confirmed;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("dry-run".equalsIgnoreCase(mode)) {
            log.info("JSON import preflight: {}", reportJson(preflight(storagePaths.getRoot())));
        } else if ("execute".equalsIgnoreCase(mode)) {
            if (!StringUtils.hasText(backupDirectory)) {
                throw new IllegalArgumentException("Set app.data-import.backup-dir to a verified full backup directory.");
            }
            ImportManifest report = execute(storagePaths.getRoot(), Path.of(backupDirectory), confirmed);
            log.info("JSON import result: {}", reportJson(report));
        } else if (!"disabled".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported app.data-import.mode: " + mode);
        }
    }

    public ImportManifest preflight(Path root) {
        ValidatedImport source = readAndValidate(root);
        return manifest(source, "DRY_RUN", zeroCounts(), "all source files validated; no database writes performed");
    }

    public ImportManifest execute(Path root, Path backupDirectory, boolean confirm) {
        if (!"database".equalsIgnoreCase(persistenceMode)) {
            throw new IllegalStateException("JSON import execution requires app.persistence.mode=database.");
        }
        if (!confirm) throw new IllegalArgumentException("Import execution requires explicit confirm=true.");
        ValidatedImport source = readAndValidate(root);
        verifyBackup(root, backupDirectory);

        ExistingManifest existing = findManifest(source.manifestId);
        if (existing != null) {
            if (!samePayload(existing.sourceCounts, source)) {
                throw new IllegalStateException("Import manifest ID exists with different source metadata.");
            }
            if ("COMPLETE".equals(existing.status)) {
                Map<String, Integer> actual = verifyDestination(source);
                return manifest(source, "COMPLETE", actual, "matching completed manifest verified; no writes repeated");
            }
            throw new IllegalStateException("An import with this manifest is " + existing.status
                    + "; restore or clean up the partial target before retrying.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM data_import_manifest", Integer.class) > 0) {
            throw new IllegalStateException("Import manifest history exists; inspect and clean the target before importing.");
        }
        assertEmptyTargets();

        String sourceCountsJson = sourceMetadataJson(source);
        jdbc.update("INSERT INTO data_import_manifest (manifest_id, status, source_counts) VALUES (?, 'IN_PROGRESS', ?)",
                source.manifestId, sourceCountsJson);
        try {
            writeMysql(source);
            writeMongo(source);
            Map<String, Integer> actual = verifyDestination(source);
            jdbc.update("UPDATE data_import_manifest SET status = 'COMPLETE', completed_at = CURRENT_TIMESTAMP(6) WHERE manifest_id = ?",
                    source.manifestId);
            return manifest(source, "COMPLETE", actual, "all source and destination counts/content checks passed");
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE data_import_manifest SET status = 'FAILED' WHERE manifest_id = ?", source.manifestId);
            throw ex;
        }
    }

    private ValidatedImport readAndValidate(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            throw new ImportValidationException("Storage root does not exist: " + root);
        }
        Path lessonsFile = root.resolve("lessons.json");
        Path categoriesFile = root.resolve("categories.json");
        Path usersFile = root.resolve("users.json");
        Path userDataDirectory = root.resolve("user-data");
        if (!Files.isRegularFile(lessonsFile) || !Files.isRegularFile(categoriesFile)
                || !Files.isRegularFile(usersFile) || !Files.isDirectory(userDataDirectory)) {
            throw new ImportValidationException("Required lessons.json, categories.json, users.json, and user-data/ sources must exist.");
        }

        try {
            ValidatedImport result = new ValidatedImport();
            result.lessons = mapper.readValue(lessonsFile.toFile(), LESSON_LIST);
            result.categories = mapper.readValue(categoriesFile.toFile(), CATEGORY_LIST);
            result.users = mapper.readValue(usersFile.toFile(), USER_LIST);
            if (result.lessons == null || result.categories == null || result.users == null) {
                throw new ImportValidationException("Source JSON top-level values must be arrays.");
            }
            validateLessons(result.lessons);
            validateCategories(result.categories);
            validateUsers(result.users);
            result.userData = readUserData(userDataDirectory, result.users);
            result.sourceHashes = calculateSourceHashes(lessonsFile, categoriesFile, usersFile, userDataDirectory);
            result.sourceCounts = new LinkedHashMap<>();
            result.sourceCounts.put("lessons", result.lessons.size());
            result.sourceCounts.put("categories", result.categories.size());
            result.sourceCounts.put("users", result.users.size());
            result.sourceCounts.put("userData", result.userData.size());
            result.manifestId = manifestId(result.sourceHashes);
            return result;
        } catch (ImportValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ImportValidationException("Unable to read or validate JSON sources under " + root + ": " + ex.getMessage(), ex);
        }
    }

    private void validateLessons(List<Lesson> lessons) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> numbers = new LinkedHashSet<>();
        for (int index = 0; index < lessons.size(); index++) {
            Lesson lesson = lessons.get(index);
            if (lesson == null || !StringUtils.hasText(lesson.getId()) || !StringUtils.hasText(lesson.getTitle())) {
                throw new ImportValidationException("lesson at index " + index + " must have a non-empty id and title");
            }
            if (!ids.add(lesson.getId())) throw new ImportValidationException("duplicate lesson id: " + lesson.getId());
            if (lesson.getId().length() > 36) throw new ImportValidationException("lesson id is too long: " + lesson.getId());
            if (!StringUtils.hasText(lesson.getNumber()) || lesson.getNumber().length() > 32
                    || !numbers.add(lesson.getNumber())) {
                throw new ImportValidationException("missing, overlong, or duplicate lesson number/id: " + lesson.getId());
            }
            if (lesson.getNumber().matches("\\d+")) {
                try {
                    if (Integer.parseInt(lesson.getNumber()) >= Integer.MAX_VALUE) {
                        throw new ImportValidationException("lesson number exceeds supported sequence range: " + lesson.getNumber());
                    }
                } catch (NumberFormatException ex) {
                    throw new ImportValidationException("lesson number exceeds supported sequence range: " + lesson.getNumber());
                }
            }
            if (!StringUtils.hasText(lesson.getUpdatedAt()) || tooLong(lesson.getUpdatedAt(), 40)) {
                throw new ImportValidationException("missing or overlong updatedAt for lesson id " + lesson.getId());
            }
            if (!StringUtils.hasText(lesson.getCategory()) || lesson.getCategory().length() > 60
                    || !StringUtils.hasText(lesson.getLevel()) || lesson.getLevel().length() > 40
                    || !Double.isFinite(lesson.getDuration()) || lesson.getDuration() < 0
                    || tooLong(lesson.getTitle(), 200) || tooLong(lesson.getTitleZh(), 200)
                    || tooLong(lesson.getAudio(), 1024) || tooLong(lesson.getCover(), 1024)
                    || tooLong(lesson.getSummary(), 1000) || tooLong(lesson.getSummaryZh(), 1000)
                    || tooLong(lesson.getWordsText(), 5000)) {
                throw new ImportValidationException("invalid or overlong lesson fields for id " + lesson.getId());
            }
            if (lesson.getTranscript() == null) lesson.setTranscript(new ArrayList<>());
            if (lesson.getKeyPhrases() == null) lesson.setKeyPhrases(new ArrayList<>());
            for (TranscriptLine line : lesson.getTranscript()) {
                if (line == null || !StringUtils.hasText(line.getText()) || !Double.isFinite(line.getStart())
                        || !Double.isFinite(line.getEnd()) || line.getStart() < 0 || line.getEnd() < line.getStart()) {
                    throw new ImportValidationException("invalid transcript line for lesson " + lesson.getId());
                }
            }
            for (KeyPhrase phrase : lesson.getKeyPhrases()) {
                if (phrase == null || !StringUtils.hasText(phrase.getPhrase())) {
                    throw new ImportValidationException("invalid key phrase for lesson " + lesson.getId());
                }
            }
        }
    }

    private void validateCategories(List<String> categories) {
        Set<String> keys = new LinkedHashSet<>();
        for (String raw : categories) {
            String name = normalizeCategory(raw);
            if (name == null || name.length() > MAX_CATEGORY_LENGTH) {
                throw new ImportValidationException("invalid category name: " + raw);
            }
            if (!keys.add(categoryKey(name))) throw new ImportValidationException("duplicate category name: " + name);
        }
    }

    private void validateUsers(List<UserStore.Account> accounts) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (UserStore.Account account : accounts) {
            if (account == null || !StringUtils.hasText(account.id) || account.id.length() > 36
                    || account.id.contains("/") || account.id.contains("\\")
                    || !StringUtils.hasText(account.username) || account.username.length() > MAX_USERNAME_LENGTH
                    || !account.username.matches("[\\p{L}\\p{N}_.-]{3,30}")
                    || account.passwordHash == null || !account.passwordHash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")
                    || !StringUtils.hasText(account.createdAt) || account.createdAt.length() > 40) {
                throw new ImportValidationException("invalid user account record");
            }
            if (!ids.add(account.id)) throw new ImportValidationException("duplicate user id: " + account.id);
            if (!names.add(account.username.toLowerCase(Locale.ROOT))) {
                throw new ImportValidationException("duplicate username: " + account.username);
            }
        }
    }

    private Map<String, ListeningData> readUserData(Path directory, List<UserStore.Account> accounts) throws IOException {
        Map<String, UserStore.Account> accountsById = accounts.stream().collect(Collectors.toMap(
                account -> account.id, Function.identity()));
        Map<String, ListeningData> data = new LinkedHashMap<>();
        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList();
        }
        for (Path file : files) {
            String filename = file.getFileName().toString();
            String userId = filename.substring(0, filename.length() - ".json".length());
            if (!accountsById.containsKey(userId)) {
                throw new ImportValidationException("listening data has no matching account: " + userId);
            }
            ListeningData userData;
            try {
                userData = mapper.readValue(file.toFile(), ListeningData.class);
            } catch (IOException ex) {
                throw new ImportValidationException("corrupt listening data for user " + userId, ex);
            }
            normalizeListeningData(userData);
            validateListeningData(userId, userData);
            try {
                sizeValidator.validate(userId, userData);
            } catch (RuntimeException ex) {
                throw new ImportValidationException("invalid listening document for user " + userId + ": " + ex.getMessage(), ex);
            }
            data.put(userId, userData);
        }
        for (UserStore.Account account : accounts) {
            if (!data.containsKey(account.id)) {
                throw new ImportValidationException("missing listening data file for user " + account.id);
            }
        }
        return data;
    }

    private void normalizeListeningData(ListeningData data) {
        if (data == null) throw new ImportValidationException("listening data document cannot be null");
        if (data.records == null) data.records = new LinkedHashMap<>();
        if (data.streams == null) data.streams = new LinkedHashMap<>();
        if (data.daily == null) data.daily = new TreeMap<>();
        if (data.hours == null) data.hours = new TreeMap<>();
        if (data.favorites == null) data.favorites = new LinkedHashMap<>();
    }

    private void validateListeningData(String userId, ListeningData data) {
        for (Map.Entry<String, ListeningData.Record> entry : data.records.entrySet()) {
            ListeningData.Record record = entry.getValue();
            if (record == null || !entry.getKey().equals(record.lessonId) || !StringUtils.hasText(record.title)
                    || tooLong(record.title, 200) || tooLong(record.titleZh, 200) || tooLong(record.category, 60)
                    || !Double.isFinite(record.duration) || record.duration < 0
                    || !Double.isFinite(record.position) || record.position < 0
                    || !Double.isFinite(record.listenedSeconds) || record.listenedSeconds < 0 || record.updatedAt < 0) {
                throw new ImportValidationException("invalid listening record for user " + userId + ", lesson " + entry.getKey());
            }
            validateInstant(record.lastListenedAt, "lastListenedAt", userId);
        }
        for (Map.Entry<String, ListeningData.Stream> entry : data.streams.entrySet()) {
            ListeningData.Stream stream = entry.getValue();
            if (stream == null || !StringUtils.hasText(stream.lessonId) || stream.sequence < 0
                    || !Double.isFinite(stream.seconds) || stream.seconds < 0) {
                throw new ImportValidationException("invalid listening stream for user " + userId + ", stream " + entry.getKey());
            }
            validateInstant(stream.lastReportedAt, "lastReportedAt", userId);
        }
        for (Map.Entry<String, Double> entry : data.daily.entrySet()) {
            try {
                java.time.LocalDate.parse(entry.getKey());
            } catch (RuntimeException ex) {
                throw new ImportValidationException("invalid daily aggregate date for user " + userId + ": " + entry.getKey());
            }
            validateAggregate(entry.getKey(), entry.getValue(), userId);
        }
        for (Map.Entry<String, Double> entry : data.hours.entrySet()) {
            try {
                int hour = Integer.parseInt(entry.getKey());
                if (hour < 0 || hour > 23) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                throw new ImportValidationException("invalid hourly aggregate key for user " + userId + ": " + entry.getKey());
            }
            validateAggregate(entry.getKey(), entry.getValue(), userId);
        }
        for (Map.Entry<String, Long> entry : data.favorites.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0) {
                throw new ImportValidationException("invalid favorite for user " + userId + ": " + entry.getKey());
            }
        }
        validateInstant(data.lastCreditedAt, "lastCreditedAt", userId);
    }

    private void validateAggregate(String key, Double value, String userId) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new ImportValidationException("invalid listening aggregate for user " + userId + ": " + key);
        }
    }

    private void validateInstant(String value, String field, String userId) {
        if (value == null) return;
        try {
            java.time.Instant.parse(value);
        } catch (RuntimeException ex) {
            throw new ImportValidationException("invalid " + field + " timestamp for user " + userId);
        }
    }

    private boolean tooLong(String value, int maximum) {
        return value != null && value.length() > maximum;
    }

    private Map<String, String> calculateSourceHashes(Path lessons, Path categories, Path users, Path userData) throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("lessons.json", sha256(lessons));
        hashes.put("categories.json", sha256(categories));
        hashes.put("users.json", sha256(users));
        try (var files = Files.list(userData)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                hashes.put("user-data/" + file.getFileName().toString(), sha256(file));
            }
        }
        return hashes;
    }

    private void verifyBackup(Path source, Path backup) {
        if (source == null || backup == null || !Files.isDirectory(backup)) {
            throw new IllegalArgumentException("A full backup directory is required before import execution.");
        }
        try {
            Path sourceReal = source.toRealPath();
            Path backupReal = backup.toRealPath();
            if (sourceReal.equals(backupReal) || sourceReal.startsWith(backupReal) || backupReal.startsWith(sourceReal)) {
                throw new IllegalArgumentException("Backup directory must be separate from the source data directory.");
            }
            Map<String, String> sourceTree = treeHashes(sourceReal);
            Map<String, String> backupTree = treeHashes(backupReal);
            if (!sourceTree.equals(backupTree)) {
                throw new IllegalArgumentException("Backup directory does not contain a complete, byte-identical copy of the data directory.");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to verify full data backup: " + ex.getMessage(), ex);
        }
    }

    private Map<String, String> treeHashes(Path root) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> !p.equals(root)).toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("symbolic links are not allowed in backup validation");
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) entries.put(relative + "/", "directory");
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) entries.put(relative, sha256(path));
                else throw new IOException("unsupported filesystem entry: " + relative);
            }
        }
        return entries;
    }

    private void assertEmptyTargets() {
        Map<String, Integer> counts = destinationCounts();
        if (counts.values().stream().anyMatch(count -> count != 0)) {
            throw new IllegalStateException("Import targets are not empty; no source data was written.");
        }
    }

    private Map<String, Integer> destinationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("lessons", jdbc.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class));
        counts.put("categories", jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class));
        counts.put("users", jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        counts.put("userData", Math.toIntExact(listeningRepository.count()));
        return counts;
    }

    private void writeMysql(ValidatedImport source) {
        transactions.executeWithoutResult(status -> {
            long sortOrder = 1;
            int maxNumber = 0;
            for (Lesson lesson : source.lessons) {
                LessonEntity entity = toLessonEntity(lesson, sortOrder++);
                lessonMapper.insert(entity);
                try {
                    maxNumber = Math.max(maxNumber, Integer.parseInt(lesson.getNumber()));
                } catch (NumberFormatException ignored) {
                    // Preserve non-numeric historical identifiers; new lesson numbering starts at 1 if none are numeric.
                }
            }
            for (String rawCategory : source.categories) {
                String name = normalizeCategory(rawCategory);
                CategoryEntity entity = new CategoryEntity();
                entity.setNameKey(categoryKey(name));
                entity.setName(name);
                categoryMapper.insert(entity);
            }
            for (UserStore.Account account : source.users) users.insert(account);

            LessonNumberSequenceEntity sequence = sequenceMapper.selectById(1);
            if (sequence == null) throw new IllegalStateException("Lesson number sequence row is missing.");
            sequence.setNextNumber(maxNumber + 1);
            sequence.setNextSortOrder(sortOrder);
            sequenceMapper.updateById(sequence);
        });
    }

    private LessonEntity toLessonEntity(Lesson lesson, long sortOrder) {
        LessonEntity entity = new LessonEntity();
        entity.setId(lesson.getId());
        entity.setNumber(lesson.getNumber());
        entity.setSortOrder(sortOrder);
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
        entity.setTranscript(lesson.getTranscript());
        entity.setWordsText(lesson.getWordsText());
        entity.setKeyPhrases(lesson.getKeyPhrases());
        entity.setVersion(0L);
        return entity;
    }

    private void writeMongo(ValidatedImport source) {
        for (Map.Entry<String, ListeningData> entry : source.userData.entrySet()) {
            listening.update(entry.getKey(), target -> {
                target.records = new LinkedHashMap<>(entry.getValue().records);
                target.streams = new LinkedHashMap<>(entry.getValue().streams);
                target.daily = new TreeMap<>(entry.getValue().daily);
                target.hours = new TreeMap<>(entry.getValue().hours);
                target.favorites = new LinkedHashMap<>(entry.getValue().favorites);
                target.lastCreditedAt = entry.getValue().lastCreditedAt;
                target.analysis = entry.getValue().analysis;
                return null;
            });
        }
    }

    private Map<String, Integer> verifyDestination(ValidatedImport source) {
        Map<String, Integer> counts = destinationCounts();
        if (!counts.equals(source.sourceCounts)) {
            throw new IllegalStateException("Destination counts do not match source counts: " + counts + " vs " + source.sourceCounts);
        }
        for (Lesson expected : source.lessons) {
            Lesson actual = lessonPersistence.findById(expected.getId()).orElse(null);
            if (actual == null || !mapper.valueToTree(expected).equals(mapper.valueToTree(actual))) {
                throw new IllegalStateException("Lesson content verification failed for " + expected.getId());
            }
        }
        for (String category : source.categories) {
            CategoryEntity actual = categoryMapper.selectByNameKey(categoryKey(normalizeCategory(category)));
            if (actual == null || !normalizeCategory(category).equals(actual.getName())) {
                throw new IllegalStateException("Category content verification failed for " + category);
            }
        }
        for (UserStore.Account expected : source.users) {
            UserStore.Account actual = users.findById(expected.id);
            if (actual == null || !expected.username.equals(actual.username)
                    || !expected.passwordHash.equals(actual.passwordHash)) {
                throw new IllegalStateException("Account content verification failed for " + expected.id);
            }
        }
        for (Map.Entry<String, ListeningData> entry : source.userData.entrySet()) {
            ListeningData actual = listening.load(entry.getKey());
            ListeningData expected = entry.getValue();
            if (!mapper.valueToTree(expected).equals(mapper.valueToTree(actual))) {
                throw new IllegalStateException("Listening content verification failed for " + entry.getKey());
            }
        }
        return counts;
    }

    private ExistingManifest findManifest(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, source_counts FROM data_import_manifest WHERE manifest_id = ?", id);
        if (rows.isEmpty()) return null;
        ExistingManifest manifest = new ExistingManifest();
        manifest.status = String.valueOf(rows.get(0).get("status"));
        manifest.sourceCounts = String.valueOf(rows.get(0).get("source_counts"));
        return manifest;
    }

    private boolean samePayload(String stored, ValidatedImport source) {
        try {
            JsonNode actual = mapper.readTree(stored);
            return actual.equals(mapper.valueToTree(payload(source)));
        } catch (IOException ex) {
            return false;
        }
    }

    private String sourceMetadataJson(ValidatedImport source) {
        try {
            return mapper.writeValueAsString(payload(source));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode source manifest.", ex);
        }
    }

    private Map<String, Object> payload(ValidatedImport source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceCounts", source.sourceCounts);
        payload.put("sourceHashes", source.sourceHashes);
        return payload;
    }

    private ImportManifest manifest(ValidatedImport source, String status, Map<String, Integer> destinationCounts,
            String validation) {
        return new ImportManifest(source.manifestId, status, source.sourceCounts, source.sourceHashes,
                destinationCounts, validation);
    }

    private Map<String, Integer> zeroCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("lessons", 0);
        counts.put("categories", 0);
        counts.put("users", 0);
        counts.put("userData", 0);
        return counts;
    }

    private String normalizeCategory(String raw) {
        return raw == null ? null : raw.trim().replaceAll("\\s+", " ");
    }

    private String categoryKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String manifestId(Map<String, String> hashes) {
        String canonical = hashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\n"));
        return sha256(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String reportJson(ImportManifest report) {
        try {
            return mapper.writeValueAsString(report);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to serialize import report.", ex);
        }
    }

    private static final class ValidatedImport {
        private List<Lesson> lessons;
        private List<String> categories;
        private List<UserStore.Account> users;
        private Map<String, ListeningData> userData;
        private Map<String, Integer> sourceCounts;
        private Map<String, String> sourceHashes;
        private String manifestId;
    }

    private static final class ExistingManifest {
        private String status;
        private String sourceCounts;
    }
}
