package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaojie.englishlearning.category.CategoryStore;
import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"app.translation.enabled=false", "spring.flyway.enabled=true"})
class CategoryPersistenceIntegrationTest extends DatabaseTestContainers {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
    }

    @Autowired
    private CategoryStore categories;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void categoryNamesAreCaseInsensitiveUniqueAndKeepFirstSpelling() throws Exception {
        categories.add("  Travel  ");
        categories.add("travel");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE name_key = ?", Integer.class,
                "travel")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM categories WHERE name_key = ?", String.class,
                "travel")).isEqualTo("Travel");
        assertThat(categories.findAll()).contains("Travel");
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("cadence-mysql-categories-");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
