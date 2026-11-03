package com.xiaojie.englishlearning.admin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "cadence.admin.authenticated";
    public static final String LOCAL_ACCESS_HEADER = "X-Admin-Local-Access";

    private final boolean allowLocalRequests;

    public AdminAuthInterceptor(boolean allowLocalRequests) {
        this.allowLocalRequests = allowLocalRequests;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        response.setHeader("Cache-Control", "no-store");

        if (allowLocalRequests && isLoopback(request.getRemoteAddr())) {
            response.setHeader(LOCAL_ACCESS_HEADER, "true");
            return true;
        }

        HttpSession session = request.getSession(false);
        Object authenticated = session == null ? null : session.getAttribute(SESSION_KEY);
        if (Boolean.TRUE.equals(authenticated)) {
            return true;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"请先登录后台。\"}");
        return false;
    }

    /** Check the socket peer address; forwarded headers are not trusted here. */
    private boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isEmpty()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
