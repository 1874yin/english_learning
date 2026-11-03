package com.xiaojie.englishlearning.user;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class ListeningController {
    private final ListeningStore listening;
    private final UserStore users;
    private final UserAnalysisService analysis;

    public ListeningController(ListeningStore listening, UserStore users, UserAnalysisService analysis) {
        this.listening = listening;
        this.users = users;
        this.analysis = analysis;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) throws IOException {
        String userId = UserAuthInterceptor.userId(request);
        Map<String, Object> profile = listening.profile(userId);
        profile.put("user", users.view(users.findById(userId)));
        profile.put("analysisConfigured", analysis.isConfigured());
        return profile;
    }

    @PostMapping("/analysis")
    public com.fasterxml.jackson.databind.JsonNode analysis(HttpServletRequest request) throws IOException {
        return analysis.generate(UserAuthInterceptor.userId(request));
    }

    @PostMapping("/listening/start")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> start(@RequestBody StartInput input, HttpServletRequest request) throws IOException {
        return Collections.singletonMap("streamId", listening.start(UserAuthInterceptor.userId(request), input.lessonId));
    }

    @PostMapping("/listening")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@RequestBody Heartbeat input, HttpServletRequest request) throws IOException {
        listening.record(UserAuthInterceptor.userId(request), input.streamId, input.sequence, input.seconds, input.position);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void importProgress(@RequestBody ImportInput input, HttpServletRequest request) throws IOException {
        listening.importProgress(UserAuthInterceptor.userId(request), input.records);
    }

    @PutMapping("/favorites/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@PathVariable String lessonId, HttpServletRequest request) throws IOException {
        listening.favorite(UserAuthInterceptor.userId(request), lessonId);
    }

    @DeleteMapping("/favorites/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@PathVariable String lessonId, HttpServletRequest request) throws IOException {
        listening.unfavorite(UserAuthInterceptor.userId(request), lessonId);
    }

    public static class StartInput { public String lessonId; }
    public static class Heartbeat {
        public String streamId;
        public int sequence;
        public double seconds;
        public double position;
    }
    public static class ImportInput { public List<ListeningStore.ImportRecord> records; }
}
