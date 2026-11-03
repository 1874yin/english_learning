package com.xiaojie.englishlearning.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserAuthInterceptor implements HandlerInterceptor {
    static final String USER_ID = "cadence.user.id";
    private static final String CSRF = "cadence.user.csrf";
    private static final String DEFAULT_SESSION_COOKIE = "JSESSIONID";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ObjectMapper mapper;
    private final org.springframework.boot.web.server.Cookie cookieConfig;

    public UserAuthInterceptor(ObjectMapper mapper, org.springframework.boot.web.server.Cookie cookieConfig) {
        this.mapper = mapper;
        this.cookieConfig = cookieConfig;
    }

    static String csrfToken(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF);
            if (token == null) {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(CSRF, token);
            }
            return token;
        }
    }

    static void rotateToken(HttpSession session) {
        session.removeAttribute(CSRF);
        csrfToken(session);
    }

    static String userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(USER_ID);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setHeader("Cache-Control", "no-store");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean anonymous = path.equals("/api/user/session") || path.equals("/api/user/login")
                || path.equals("/api/user/register");
        if (!anonymous && userId(request) == null) {
            return reject(response, 401, "请先登录。" );
        }
        String expectedUser = request.getHeader("X-User-Id");
        if (!anonymous && expectedUser != null && !expectedUser.equals(userId(request))) {
            response.setStatus(409);
            response.setContentType("application/json;charset=UTF-8");
            java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
            body.put("code", "ACCOUNT_CHANGED");
            body.put("message", "登录账号已变化，请刷新后重试。" );
            mapper.writeValue(response.getWriter(), body);
            return false;
        }
        renewSessionCookie(request, response, path);
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            HttpSession session = request.getSession(false);
            String supplied = request.getHeader("X-CSRF-Token");
            if (session == null || supplied == null || !MessageDigest.isEqual(
                    csrfToken(session).getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                return reject(response, 403, "页面已过期，请刷新后重试。" );
            }
        }
        return true;
    }

    /**
     * Re-send the session cookie with its full Max-Age on every authenticated request so
     * the seven day window slides forward. Login, registration and logout are skipped:
     * those handlers rotate the session id and Tomcat already writes the new cookie.
     */
    private void renewSessionCookie(HttpServletRequest request, HttpServletResponse response, String path) {
        if (userId(request) == null || path.equals("/api/user/login")
                || path.equals("/api/user/register") || path.equals("/api/user/logout")) {
            return;
        }
        Duration maxAge = cookieConfig.getMaxAge();
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero() || maxAge.getSeconds() > Integer.MAX_VALUE) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Cookie cookie = new Cookie(cookieConfig.getName() == null ? DEFAULT_SESSION_COOKIE : cookieConfig.getName(),
                session.getId());
        String cookiePath = cookieConfig.getPath();
        if (cookiePath == null || cookiePath.isEmpty()) {
            cookiePath = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        }
        cookie.setPath(cookiePath);
        if (cookieConfig.getDomain() != null) {
            cookie.setDomain(cookieConfig.getDomain());
        }
        cookie.setMaxAge((int) maxAge.getSeconds());
        if (Boolean.TRUE.equals(cookieConfig.getHttpOnly())) {
            cookie.setHttpOnly(true);
        }
        cookie.setSecure(Boolean.TRUE.equals(cookieConfig.getSecure()) || request.isSecure());
        response.addCookie(cookie);
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), Collections.singletonMap("message", message));
        return false;
    }
}
