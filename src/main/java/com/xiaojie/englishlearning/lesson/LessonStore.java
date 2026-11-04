package com.xiaojie.englishlearning.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import com.xiaojie.englishlearning.admin.AdminValidationException;
import com.xiaojie.englishlearning.persistence.LessonPersistence;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LessonStore {

    private static final Logger log = LoggerFactory.getLogger(LessonStore.class);

    private static final long MAX_COVER_SIZE = 5L * 1024 * 1024;
    private static final int MAX_COVER_DIMENSION = 8000;
    private static final Set<String> COVER_EXTENSIONS = new HashSet<String>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "avif"));

    private final StoragePaths paths;
    private final ObjectMapper objectMapper;
    private final LessonPersistence persistence;
    private final List<Lesson> lessons = new ArrayList<Lesson>();

    public LessonStore(StoragePaths paths, ObjectMapper objectMapper) {
        this(paths, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LessonStore(StoragePaths paths, ObjectMapper objectMapper, LessonPersistence persistence) {
        this.paths = paths;
        this.objectMapper = objectMapper;
        this.persistence = persistence;
    }

    @PostConstruct
    public synchronized void load() {
        if (persistence != null) {
            return;
        }
        lessons.clear();
        Path file = paths.getLessonsFile();
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            List<Lesson> loaded = objectMapper.readValue(input, new TypeReference<List<Lesson>>() { });
            if (loaded != null) {
                for (Lesson lesson : loaded) {
                    validateStoredLesson(lesson);
                    lessons.add(lesson);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load lesson library from " + file, ex);
        }
    }

    public synchronized List<Lesson> findAll() {
        if (persistence != null) {
            return persistence.findAll();
        }
        return new ArrayList<Lesson>(lessons);
    }

    public synchronized Optional<Lesson> findById(String id) {
        if (persistence != null) {
            return persistence.findById(id);
        }
        for (Lesson lesson : lessons) {
            if (lesson.getId().equals(id)) {
                return Optional.of(lesson);
            }
        }
        return Optional.empty();
    }

    public void checkWritable() {
        if (persistence != null) persistence.checkWritable();
    }

    public synchronized String nextNumber() {
        if (persistence != null) {
            return persistence.nextNumber();
        }
        int max = 0;
        for (Lesson lesson : lessons) {
            try {
                max = Math.max(max, Integer.parseInt(lesson.getNumber()));
            } catch (NumberFormatException ex) {
                /* Keep the numeric maximum from the remaining lessons. */
            }
        }
        return String.format(Locale.ROOT, "%02d", max + 1);
    }

    public synchronized Lesson insert(Lesson lesson) throws IOException {
        checkWritable();
        if (persistence != null) {
            return persistence.insert(lesson);
        }
        if (lesson.getNumber() == null) {
            lesson.setNumber(nextNumber());
        }
        lessons.add(lesson);
        try {
            persist();
        } catch (IOException ex) {
            lessons.remove(lesson);
            throw ex;
        }
        return lesson;
    }

    public synchronized Lesson replace(Lesson lesson) throws IOException {
        checkWritable();
        if (persistence != null) {
            return persistence.replace(lesson);
        }
        for (int i = 0; i < lessons.size(); i += 1) {
            if (lessons.get(i).getId().equals(lesson.getId())) {
                Lesson previous = lessons.get(i);
                lessons.set(i, lesson);
                try {
                    persist();
                } catch (IOException ex) {
                    lessons.set(i, previous);
                    throw ex;
                }
                return lesson;
            }
        }
        throw new NoSuchElementException("素材不存在：" + lesson.getId());
    }

    /**
     * Applies translations only if the transcript is still the one that was
     * translated. This prevents the startup task from overwriting a lesson
     * that was edited while the translation request was in flight.
     */
    public synchronized boolean updateTranslations(String id, List<String> expectedTexts,
            List<String> translations) throws IOException {
        checkWritable();
        if (persistence != null) {
            return persistence.updateTranslations(id, expectedTexts, translations);
        }
        Optional<Lesson> found = findById(id);
        if (!found.isPresent()) {
            return false;
        }
        Lesson lesson = found.get();
        List<TranscriptLine> lines = lesson.getTranscript();
        if (lines == null || expectedTexts == null || translations == null
                || lines.size() != expectedTexts.size()
                || translations.size() != expectedTexts.size()) {
            return false;
        }

        List<String> previous = new ArrayList<String>(lines.size());
        for (int i = 0; i < lines.size(); i += 1) {
            TranscriptLine line = lines.get(i);
            if (line == null || line.getText() == null
                    || !line.getText().equals(expectedTexts.get(i))) {
                return false;
            }
            previous.add(line.getTranslation());
        }

        for (int i = 0; i < lines.size(); i += 1) {
            lines.get(i).setTranslation(translations.get(i));
        }
        try {
            persist();
        } catch (IOException ex) {
            for (int i = 0; i < lines.size(); i += 1) {
                lines.get(i).setTranslation(previous.get(i));
            }
            throw ex;
        }
        return true;
    }

    /**
     * Removes every stored Chinese subtitle. Used once when the translation
     * provider changes, so subtitles from the old provider are not kept.
     * Returns the number of subtitle lines that were cleared.
     */
    public synchronized int clearAllTranslations() throws IOException {
        checkWritable();
        if (persistence != null) {
            return persistence.clearAllTranslations();
        }
        List<TranscriptLine> changedLines = new ArrayList<TranscriptLine>();
        List<String> previous = new ArrayList<String>();
        for (Lesson lesson : lessons) {
            List<TranscriptLine> lines = lesson.getTranscript();
            if (lines == null) {
                continue;
            }
            for (TranscriptLine line : lines) {
                if (line != null && line.getTranslation() != null) {
                    changedLines.add(line);
                    previous.add(line.getTranslation());
                    line.setTranslation(null);
                }
            }
        }
        if (changedLines.isEmpty()) {
            return 0;
        }
        try {
            persist();
        } catch (IOException ex) {
            for (int i = 0; i < changedLines.size(); i += 1) {
                changedLines.get(i).setTranslation(previous.get(i));
            }
            throw ex;
        }
        return changedLines.size();
    }

    public synchronized LessonPage findPage(int page, int size) {
        if (persistence != null) {
            return persistence.findPage(page, size);
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int total = lessons.size();
        int from = (int) Math.min((long) safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<LessonSummary> items = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            items.add(LessonSummary.from(lessons.get(i)));
        }
        return new LessonPage(items, safePage, safeSize, total);
    }

    public synchronized StoredAudio storeAudio(MultipartFile audio, String lessonId) throws IOException {
        if (audio == null || audio.isEmpty()) {
            throw new AdminValidationException("请选择 MP3 音频文件。");
        }
        String originalName = audio.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            throw new AdminValidationException("只支持 MP3 音频文件。");
        }

        Path temporary = paths.getAudioDir().resolve(".upload-" + UUID.randomUUID().toString() + ".tmp");
        Path destination = null;
        try {
            audio.transferTo(temporary.toFile());
            if (Files.size(temporary) == 0) {
                throw new AdminValidationException("音频文件为空。");
            }

            long durationMs;
            try {
                Mp3File mp3 = new Mp3File(temporary.toFile());
                durationMs = mp3.getLengthInMilliseconds();
            } catch (InvalidDataException | UnsupportedTagException ex) {
                throw new AdminValidationException("无法识别这个 MP3 文件，请确认音频可以正常播放。");
            }
            if (durationMs <= 0) {
                throw new AdminValidationException("无法读取音频时长。");
            }

            String filename = lessonId + "-" + System.currentTimeMillis() + ".mp3";
            destination = paths.getAudioDir().resolve(filename);
            while (Files.exists(destination)) {
                filename = lessonId + "-" + System.currentTimeMillis() + "-"
                        + UUID.randomUUID().toString().substring(0, 6) + ".mp3";
                destination = paths.getAudioDir().resolve(filename);
            }
            move(temporary, destination);

            double duration = Math.round(durationMs / 10.0) / 100.0;
            return new StoredAudio(duration, "/media/" + filename, filename);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized StoredCover storeCover(MultipartFile cover, String lessonId) throws IOException {
        if (cover == null || cover.isEmpty()) {
            throw new AdminValidationException("请选择封面图片。");
        }
        String originalName = cover.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!COVER_EXTENSIONS.contains(extension)) {
            throw new AdminValidationException("封面只支持 JPG、PNG、GIF 或 AVIF 图片。");
        }

        Path temporary = paths.getCoverDir().resolve(".upload-" + UUID.randomUUID().toString() + ".tmp");
        Path destination = null;
        try {
            cover.transferTo(temporary.toFile());
            long size = Files.size(temporary);
            if (size == 0) {
                throw new AdminValidationException("封面图片为空。");
            }
            if (size > MAX_COVER_SIZE) {
                throw new AdminValidationException("封面图片请控制在 5 MB 以内。");
            }

            CoverSize dimensions = "avif".equals(extension)
                    ? readAvifSize(temporary)
                    : readCoverSize(temporary);
            if (dimensions == null) {
                throw new AdminValidationException("无法识别这个封面图片，请换一张 JPG、PNG、GIF 或 AVIF。");
            }
            if (dimensions.width > MAX_COVER_DIMENSION || dimensions.height > MAX_COVER_DIMENSION) {
                throw new AdminValidationException("封面尺寸过大，宽高请控制在 8000 像素以内。");
            }

            String storedExtension = "jpeg".equals(extension) ? "jpg" : extension;
            String filename = lessonId + "-" + System.currentTimeMillis() + "." + storedExtension;
            destination = paths.getCoverDir().resolve(filename);
            while (Files.exists(destination)) {
                filename = lessonId + "-" + System.currentTimeMillis() + "-"
                        + UUID.randomUUID().toString().substring(0, 6) + "." + storedExtension;
                destination = paths.getCoverDir().resolve(filename);
            }
            move(temporary, destination);
            return new StoredCover("/covers/" + filename, filename);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized void deleteAudio(String audioUrl) {
        deleteStoredFile(paths.getAudioDir(), "/media/", audioUrl);
    }

    public synchronized void deleteCover(String coverUrl) {
        deleteStoredFile(paths.getCoverDir(), "/covers/", coverUrl);
    }

    private void deleteStoredFile(Path directory, String urlPrefix, String url) {
        if (url == null || !url.startsWith(urlPrefix)) {
            return;
        }
        String filename = url.substring(urlPrefix.length());
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return;
        }
        Path file = directory.resolve(filename).normalize();
        if (!directory.equals(file.getParent())) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete replaced media file {}", file, ex);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private CoverSize readCoverSize(Path file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new CoverSize(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * The JDK's ImageIO has no AVIF reader. The browser handles decoding, so the
     * server only validates the ISO-BMFF file type and reads the ispe dimensions.
     */
    private CoverSize readAvifSize(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            if (!isAvifFile(data)) {
                return null;
            }

            int maxWidth = 0;
            int maxHeight = 0;
            for (int typeOffset = 4; typeOffset + 16 <= data.length; typeOffset += 1) {
                if (!matches(data, typeOffset, "ispe")) {
                    continue;
                }
                long boxSize = readUInt(data, typeOffset - 4);
                long boxEnd = typeOffset - 4L + boxSize;
                if (boxSize < 20 || boxEnd > data.length) {
                    continue;
                }
                long width = readUInt(data, typeOffset + 8);
                long height = readUInt(data, typeOffset + 12);
                if (width > 0 && height > 0
                        && width <= Integer.MAX_VALUE && height <= Integer.MAX_VALUE) {
                    maxWidth = Math.max(maxWidth, (int) width);
                    maxHeight = Math.max(maxHeight, (int) height);
                }
            }
            return maxWidth > 0 ? new CoverSize(maxWidth, maxHeight) : null;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private boolean isAvifFile(byte[] data) {
        if (data.length < 16 || !matches(data, 4, "ftyp")) {
            return false;
        }

        long boxSize = readUInt(data, 0);
        int majorBrandOffset = 8;
        int compatibleBrandOffset = 16;
        if (boxSize == 1) {
            if (data.length < 24) {
                return false;
            }
            long high = readUInt(data, 8);
            long low = readUInt(data, 12);
            boxSize = high == 0 ? low : Long.MAX_VALUE;
            majorBrandOffset = 16;
            compatibleBrandOffset = 24;
        }
        if (boxSize != 0 && (boxSize < 16 || boxSize > data.length)) {
            return false;
        }

        int end = boxSize == 0 ? data.length : (int) boxSize;
        if (majorBrandOffset + 4 > end) {
            return false;
        }
        if (isAvifBrand(data, majorBrandOffset)) {
            return true;
        }
        for (int offset = compatibleBrandOffset; offset + 4 <= end; offset += 4) {
            if (isAvifBrand(data, offset)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAvifBrand(byte[] data, int offset) {
        return matches(data, offset, "avif") || matches(data, offset, "avis");
    }

    private boolean matches(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i += 1) {
            if (data[offset + i] != (byte) value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private long readUInt(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff) << 24)
                | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8)
                | (long) (data[offset + 3] & 0xff);
    }

    public StoragePaths getPaths() {
        return paths;
    }

    private void persist() throws IOException {
        Path file = paths.getLessonsFile();
        Path temporary = paths.getRoot().resolve("lessons.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), lessons);
        move(temporary, file);
    }

    private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validateStoredLesson(Lesson lesson) {
        if (lesson == null || isBlank(lesson.getId()) || isBlank(lesson.getTitle())) {
            throw new IllegalStateException("Lesson library contains an entry without an id or title");
        }
        if (lesson.getTranscript() == null) {
            lesson.setTranscript(Collections.<TranscriptLine>emptyList());
        }
        if (lesson.getKeyPhrases() == null) {
            lesson.setKeyPhrases(Collections.<KeyPhrase>emptyList());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class StoredAudio {

        private final double duration;
        private final String url;
        private final String filename;

        public StoredAudio(double duration, String url, String filename) {
            this.duration = duration;
            this.url = url;
            this.filename = filename;
        }

        public double getDuration() {
            return duration;
        }

        public String getUrl() {
            return url;
        }

        public String getFilename() {
            return filename;
        }
    }

    public static class StoredCover {

        private final String url;
        private final String filename;

        public StoredCover(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }

        public String getUrl() {
            return url;
        }

        public String getFilename() {
            return filename;
        }
    }

    private static class CoverSize {

        private final int width;
        private final int height;

        private CoverSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
