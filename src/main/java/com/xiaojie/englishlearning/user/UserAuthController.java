package com.xiaojie.englishlearning.user;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserAuthController {
    private final UserStore users;
    private final java.time.Clock clock;
    private final Duration sessionLifetime;
    private final Map<String, Attempt> attempts = new LinkedHashMap<>();

    public UserAuthController(UserStore users, java.time.Clock clock, ServerProperties serverProperties) {
        this.users = users;
        this.clock = clock;
        this.sessionLifetime = serverProperties.getServlet().getSession().getCookie().getMaxAge();
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        return sessionView(request.getSession());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody Credentials credentials, HttpServletRequest request) throws IOException {
        checkAttempt(request, "register", 20);
        return signIn(users.register(credentials.username, credentials.password), request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Credentials credentials, HttpServletRequest request) {
        checkAttempt(request, "login", 10);
        return signIn(users.authenticate(credentials.username, credentials.password), request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        session.removeAttribute(UserAuthInterceptor.USER_ID);
        session.setMaxInactiveInterval(anonymousSessionTimeout(request));
        UserAuthInterceptor.rotateToken(session);
        request.changeSessionId();
    }

    private Map<String, Object> signIn(UserStore.Account account, HttpServletRequest request) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(UserAuthInterceptor.USER_ID, account.id);
        if (sessionLifetime != null && !sessionLifetime.isNegative() && !sessionLifetime.isZero()) {
            session.setMaxInactiveInterval((int) sessionLifetime.getSeconds());
        }
        UserAuthInterceptor.rotateToken(session);
        return sessionView(session);
    }

    private static int anonymousSessionTimeout(HttpServletRequest request) {
        int minutes = request.getServletContext().getSessionTimeout();
        return minutes > 0 ? minutes * 60 : -1;
    }

    private Map<String, Object> sessionView(HttpSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        UserStore.Account account = users.findById((String) session.getAttribute(UserAuthInterceptor.USER_ID));
        body.put("user", account == null ? com.fasterxml.jackson.databind.node.NullNode.instance : users.view(account));
        body.put("csrfToken", UserAuthInterceptor.csrfToken(session));
        return body;
    }

    private synchronized void checkAttempt(HttpServletRequest request, String action, int limit) {
        long now = clock.millis();
        long window = 15 * 60 * 1000L;
        attempts.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= window);
        String key = action + ":" + request.getRemoteAddr();
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            if (attempts.size() >= 10000) attempts.remove(attempts.keySet().iterator().next());
            attempt = new Attempt(); attempt.startedAt = now; attempts.put(key, attempt);
        }
        if (attempt.count >= limit) throw new UserException(HttpStatus.TOO_MANY_REQUESTS, "账号操作过于频繁，请 15 分钟后再试。" );
        attempt.count++;
    }

    private static class Attempt {
        long startedAt;
        int count;
    }

    public static class Credentials {
        public String username;
        public String password;
    }
}
