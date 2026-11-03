package com.xiaojie.englishlearning.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StoragePaths {

    private final Path root;
    private final Path audioDir;
    private final Path coverDir;
    private final Path lessonsFile;
    private final Path categoriesFile;
    private final Path translationStateFile;

    public StoragePaths(@Value("${app.storage.root:data}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.audioDir = root.resolve("audio");
        this.coverDir = root.resolve("covers");
        this.lessonsFile = root.resolve("lessons.json");
        this.categoriesFile = root.resolve("categories.json");
        this.translationStateFile = root.resolve("translation-state.json");
        try {
            Files.createDirectories(audioDir);
            Files.createDirectories(coverDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create storage directory under " + root, ex);
        }
    }

    public Path getRoot() {
        return root;
    }

    public Path getAudioDir() {
        return audioDir;
    }

    public Path getCoverDir() {
        return coverDir;
    }

    public Path getLessonsFile() {
        return lessonsFile;
    }

    public Path getCategoriesFile() {
        return categoriesFile;
    }

    public Path getTranslationStateFile() {
        return translationStateFile;
    }

    public String getAudioLocation() {
        String location = audioDir.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
