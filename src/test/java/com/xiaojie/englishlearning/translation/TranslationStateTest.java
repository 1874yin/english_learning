package com.xiaojie.englishlearning.translation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationStateTest {

    @TempDir
    Path tempDir;

    private StoragePaths paths;
    private TranslationState state;

    @BeforeEach
    void setUp() {
        paths = new StoragePaths(tempDir.toString());
        state = new TranslationState(paths, new ObjectMapper());
    }

    @Test
    void isNotCurrentWhenThereIsNoStateFile() {
        assertThat(state.isCurrent()).isFalse();
        assertThat(state.readStored()).isNull();
    }

    @Test
    void exposesTheStoredProviderAndVersion() throws IOException {
        state.markCurrent();

        TranslationState.StoredState stored = state.readStored();

        assertThat(stored).isNotNull();
        assertThat(stored.getProvider()).isEqualTo("deepseek");
        assertThat(stored.getVersion()).isEqualTo(1);
    }

    @Test
    void exposesAStoredMarkerFromAnotherProvider() throws IOException {
        Files.write(paths.getTranslationStateFile(),
                "{\"provider\":\"mymemory\",\"version\":1}".getBytes(StandardCharsets.UTF_8));

        TranslationState.StoredState stored = state.readStored();

        assertThat(stored).isNotNull();
        assertThat(stored.getProvider()).isEqualTo("mymemory");
        assertThat(stored.getVersion()).isEqualTo(1);
        assertThat(state.isCurrent()).isFalse();
    }

    @Test
    void isCurrentAfterTheMarkerIsWritten() throws IOException {
        state.markCurrent();

        assertThat(Files.exists(paths.getTranslationStateFile())).isTrue();
        assertThat(new TranslationState(paths, new ObjectMapper()).isCurrent()).isTrue();
    }

    @Test
    void isNotCurrentWhenTheMarkerWasWrittenByAnotherProvider() throws IOException {
        Files.write(paths.getTranslationStateFile(),
                "{\"provider\":\"mymemory\",\"version\":1}".getBytes(StandardCharsets.UTF_8));

        assertThat(state.isCurrent()).isFalse();
    }

    @Test
    void isNotCurrentWhenTheMarkerHasAnOlderVersion() throws IOException {
        Files.write(paths.getTranslationStateFile(),
                "{\"provider\":\"deepseek\",\"version\":0}".getBytes(StandardCharsets.UTF_8));

        assertThat(state.isCurrent()).isFalse();
    }

    @Test
    void isNotCurrentWhenTheMarkerIsCorrupted() throws IOException {
        Files.write(paths.getTranslationStateFile(),
                "not json".getBytes(StandardCharsets.UTF_8));

        assertThat(state.isCurrent()).isFalse();
        assertThat(state.readStored()).isNull();
    }
}
