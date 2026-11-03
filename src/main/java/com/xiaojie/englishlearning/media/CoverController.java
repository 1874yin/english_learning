package com.xiaojie.englishlearning.media;

import com.xiaojie.englishlearning.storage.StoragePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CoverController {

    private final StoragePaths storagePaths;

    public CoverController(StoragePaths storagePaths) {
        this.storagePaths = storagePaths;
    }

    @GetMapping("/covers/{filename:.+}")
    public ResponseEntity<Resource> cover(@PathVariable String filename) {
        if (!isSafeFilename(filename)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path file = storagePaths.getCoverDir().resolve(filename).normalize();
        if (!storagePaths.getCoverDir().equals(file.getParent()) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return ResponseEntity.ok()
                .contentType(mediaTypeFor(filename))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    private boolean isSafeFilename(String filename) {
        return filename != null && !filename.isEmpty()
                && !filename.contains("/") && !filename.contains("\\")
                && !filename.contains("..");
    }

    private MediaType mediaTypeFor(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if ("avif".equals(extension)) {
            return MediaType.parseMediaType("image/avif");
        }
        if ("png".equals(extension)) {
            return MediaType.IMAGE_PNG;
        }
        if ("gif".equals(extension)) {
            return MediaType.IMAGE_GIF;
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
