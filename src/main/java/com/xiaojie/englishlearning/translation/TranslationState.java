package com.xiaojie.englishlearning.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/**
 * Records which translation provider produced the Chinese subtitles stored in
 * the lesson library. When the provider changes, the old subtitles are cleared
 * once and regenerated with the new provider.
 */
@Component
public class TranslationState {

    static final String PROVIDER = "deepseek";
    static final int VERSION = 1;

    private static final String TEMP_FILE = "translation-state.json.tmp";

    private final StoragePaths paths;
    private final ObjectMapper objectMapper;

    public TranslationState(StoragePaths paths, ObjectMapper objectMapper) {
        this.paths = paths;
        this.objectMapper = objectMapper;
    }

    /**
     * True when the stored subtitles were produced by the current provider and
     * do not need to be cleared.
     */
    public boolean isCurrent() {
        StoredState stored = readStored();
        return stored != null
                && PROVIDER.equals(stored.getProvider())
                && stored.getVersion() != null
                && stored.getVersion() == VERSION;
    }

    /**
     * Reads the marker written by the last successful migration, or null when
     * the marker is missing, unreadable or malformed.
     */
    public StoredState readStored() {
        Path file = paths.getTranslationStateFile();
        if (!Files.exists(file)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            JsonNode node = objectMapper.readTree(input);
            if (node == null || !node.isObject()) {
                return null;
            }
            JsonNode providerNode = node.path("provider");
            JsonNode versionNode = node.path("version");
            String provider = providerNode.isTextual() ? providerNode.asText() : null;
            Integer version = versionNode.isIntegralNumber() ? versionNode.asInt() : null;
            return new StoredState(provider, version);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    public static final class StoredState {

        private final String provider;
        private final Integer version;

        public StoredState(String provider, Integer version) {
            this.provider = provider;
            this.version = version;
        }

        public String getProvider() {
            return provider;
        }

        public Integer getVersion() {
            return version;
        }
    }

    public void markCurrent() throws IOException {
        Path file = paths.getTranslationStateFile();
        Path temporary = paths.getRoot().resolve(TEMP_FILE);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("provider", PROVIDER);
        node.put("version", VERSION);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), node);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
