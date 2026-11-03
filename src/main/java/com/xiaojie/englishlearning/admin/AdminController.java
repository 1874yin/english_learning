package com.xiaojie.englishlearning.admin;

import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonInput;
import com.xiaojie.englishlearning.lesson.LessonService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final LessonService lessonService;
    private final String adminPassword;

    public AdminController(LessonService lessonService,
            @Value("${app.admin.password:}") String adminPassword) {
        this.lessonService = lessonService;
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        if (this.adminPassword.trim().isEmpty()) {
            log.warn("ADMIN_PASSWORD is blank; admin login is disabled until it is configured.");
        } else if ("cadence-admin".equals(this.adminPassword)) {
            log.warn("The admin password is the default value. Set ADMIN_PASSWORD before deploying.");
        }
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiError> login(@RequestBody(required = false) LoginRequest body,
            HttpServletRequest request) {
        String supplied = body == null || body.getPassword() == null ? "" : body.getPassword();
        if (!passwordMatches(supplied)) {
            pauseAfterFailedLogin();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("密码不正确。"));
        }

        HttpSession session = request.getSession(true);
        try {
            request.changeSessionId();
        } catch (IllegalStateException ex) {
            /* The session was created in this request; there is no previous id to rotate. */
        }
        session.setAttribute(AdminAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons")
    public List<AdminLessonSummary> list() {
        List<AdminLessonSummary> result = new ArrayList<AdminLessonSummary>();
        for (Lesson lesson : lessonService.findAll()) {
            result.add(AdminLessonSummary.from(lesson));
        }
        return result;
    }

    @GetMapping("/lessons/{id}")
    public Lesson get(@PathVariable String id) {
        return lessonService.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("素材不存在：" + id));
    }

    @PostMapping(value = "/lessons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Lesson> create(
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "cover", required = false) MultipartFile cover,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "titleZh", required = false) String titleZh,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "summary", required = false) String summary,
            @RequestParam(value = "summaryZh", required = false) String summaryZh,
            @RequestParam(value = "transcriptText", required = false) String transcriptText,
            @RequestParam(value = "wordsText", required = false) String wordsText)
            throws IOException {
        LessonInput input = input(title, titleZh, category, level, summary, summaryZh,
                transcriptText, wordsText);
        Lesson lesson = lessonService.create(input, audio, cover);
        return ResponseEntity.status(HttpStatus.CREATED).body(lesson);
    }

    @PutMapping(value = "/lessons/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Lesson update(@PathVariable String id,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "cover", required = false) MultipartFile cover,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "titleZh", required = false) String titleZh,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "summary", required = false) String summary,
            @RequestParam(value = "summaryZh", required = false) String summaryZh,
            @RequestParam(value = "transcriptText", required = false) String transcriptText,
            @RequestParam(value = "wordsText", required = false) String wordsText)
            throws IOException {
        LessonInput input = input(title, titleZh, category, level, summary, summaryZh,
                transcriptText, wordsText);
        return lessonService.update(id, input, audio, cover);
    }

    private LessonInput input(String title, String titleZh, String category, String level,
            String summary, String summaryZh, String transcriptText, String wordsText) {
        LessonInput input = new LessonInput();
        input.setTitle(title);
        input.setTitleZh(titleZh);
        input.setCategory(category);
        input.setLevel(level);
        input.setSummary(summary);
        input.setSummaryZh(summaryZh);
        input.setTranscriptText(transcriptText);
        input.setWordsText(wordsText);
        return input;
    }

    private boolean passwordMatches(String supplied) {
        String expected = adminPassword;
        if (expected.trim().isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void pauseAfterFailedLogin() {
        try {
            Thread.sleep(350);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public static class LoginRequest {

        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
