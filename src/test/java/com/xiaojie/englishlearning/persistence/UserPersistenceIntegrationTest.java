package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import com.xiaojie.englishlearning.user.UserException;
import com.xiaojie.englishlearning.user.UserStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"app.translation.enabled=false", "spring.flyway.enabled=true"})
class UserPersistenceIntegrationTest extends DatabaseTestContainers {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
    }

    @Autowired
    private UserStore users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void preservesBcryptHashAndEnforcesCaseInsensitiveUsernameUniqueness() throws Exception {
        UserStore.Account account = users.register("Mixed_User", "correct-password");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class,
                account.id)).isEqualTo(1);
        String storedHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class,
                account.id);
        assertThat(storedHash).startsWith("$2").isNotEqualTo("correct-password");
        assertThat(users.authenticate("mixed_user", "correct-password").id).isEqualTo(account.id);
        assertThatThrownBy(() -> users.register("mixed_user", "another-password"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("已被注册");
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("cadence-mysql-users-");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
