package com.xiaojie.englishlearning.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"app.translation.enabled=false", "app.user-analysis.api-key="})
@AutoConfigureMockMvc
class UserAuthApiTest {
    private static final Path ROOT = createRoot();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registrationPersistsOnlyHashedPasswordAndLogsIn() throws Exception {
        Guest guest = guest();
        String oldSessionId = guest.session.getId();
        String name = "user_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mvc.perform(post("/api/user/register").session(guest.session)
                .header("X-CSRF-Token", guest.csrf).contentType(MediaType.APPLICATION_JSON)
                .content(credentials(name, "correct-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value(name))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(get("/api/user/session").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(name));
        mvc.perform(get("/api/user/profile").session(session).header("X-User-Id", "another-account"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_CHANGED"));
        String stored = new String(Files.readAllBytes(ROOT.resolve("users.json")), StandardCharsets.UTF_8);
        assertThat(stored).contains(name).doesNotContain("correct-password").contains("$2");
    }

    @Test
    void duplicateUsernamesAreCaseInsensitiveAndPasswordsMustBeStrongEnough() throws Exception {
        Guest g = guest();
        String name = "Mixed_" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/user/register").session(g.session).header("X-CSRF-Token", g.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials(name, "correct-password")))
                .andExpect(status().isCreated());
        Guest other = guest();
        mvc.perform(post("/api/user/register").session(other.session).header("X-CSRF-Token", other.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials(name.toLowerCase(), "correct-password")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/user/register").session(other.session).header("X-CSRF-Token", other.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials("bad-password", "123")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/user/register").session(other.session).header("X-CSRF-Token", other.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials("<script>alert(1)</script>", "correct-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticationRequiresCsrfAndLogoutRemovesUserAccess() throws Exception {
        Guest g = guest();
        mvc.perform(post("/api/user/register").session(g.session).contentType(MediaType.APPLICATION_JSON)
                .content(credentials("csrfuser", "correct-password"))).andExpect(status().isForbidden());
        String name = "logout_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult reg = mvc.perform(post("/api/user/register").session(g.session).header("X-CSRF-Token", g.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials(name, "correct-password")))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession s = (MockHttpSession) reg.getRequest().getSession(false);
        String csrf = mapper.readTree(reg.getResponse().getContentAsString()).path("csrfToken").asText();
        mvc.perform(post("/api/user/logout").session(s).header("X-CSRF-Token", csrf))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/user/session").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
        Guest login = guest();
        mvc.perform(post("/api/user/login").session(login.session).header("X-CSRF-Token", login.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials(name, "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/user/login").session(login.session).header("X-CSRF-Token", login.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials(name.toUpperCase(), "correct-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(name));
    }

    @Test
    void userSessionDoesNotGrantAdminRightsAndAdminSessionIsNotAUser() throws Exception {
        Guest g = guest();
        g.session.setAttribute("cadence.admin.authenticated", true);
        mvc.perform(get("/api/user/session").session(g.session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user").isEmpty());
        String name = "admin_" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/user/register").session(g.session).header("X-CSRF-Token", g.csrf)
                .contentType(MediaType.APPLICATION_JSON).content(credentials(name, "correct-password")))
                .andExpect(status().isCreated());
        g.session.removeAttribute("cadence.admin.authenticated");
        mvc.perform(post("/api/admin/categories").session(g.session)
                .with(request -> { request.setRemoteAddr("203.0.113.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Not allowed\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsAreRateLimitedWithoutBlockingOtherClients() throws Exception {
        Guest g = guest();
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/user/login").session(g.session).header("X-CSRF-Token", g.csrf)
                    .with(request -> { request.setRemoteAddr("203.0.113.77"); return request; })
                    .contentType(MediaType.APPLICATION_JSON).content(credentials("unknown-user", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/user/login").session(g.session).header("X-CSRF-Token", g.csrf)
                .with(request -> { request.setRemoteAddr("203.0.113.77"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(credentials("unknown-user", "wrong-password")))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/user/login").session(g.session).header("X-CSRF-Token", g.csrf)
                .with(request -> { request.setRemoteAddr("203.0.113.78"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(credentials("unknown-user", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestsRenewSevenDaySessionCookie() throws Exception {
        Guest guest = guest();
        String name = "cookie_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = mvc.perform(post("/api/user/register").session(guest.session)
                .header("X-CSRF-Token", guest.csrf).contentType(MediaType.APPLICATION_JSON)
                .content(credentials(name, "correct-password")))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        MvcResult result = mvc.perform(get("/api/user/session").session(session))
                .andExpect(status().isOk()).andReturn();

        Cookie cookie = result.getResponse().getCookie("JSESSIONID");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(session.getId());
        assertThat(cookie.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void anonymousRequestsDoNotRenewSessionCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/user/session"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }

    private Guest guest() throws Exception {
        MvcResult result = mvc.perform(get("/api/user/session")).andExpect(status().isOk()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new Guest((MockHttpSession) result.getRequest().getSession(false), body.path("csrfToken").asText());
    }
    private String credentials(String username, String password) throws Exception {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("username", username); values.put("password", password);
        return mapper.writeValueAsString(values);
    }
    private static Path createRoot() {
        try { return Files.createTempDirectory("cadence-user-auth-"); }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private static class Guest {
        final MockHttpSession session;
        final String csrf;
        Guest(MockHttpSession session, String csrf) { this.session = session; this.csrf = csrf; }
    }
}
