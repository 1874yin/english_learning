package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import java.util.List;
import org.bson.Document;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "app.translation.enabled=false",
        "app.user-analysis.enabled=false",
        "spring.flyway.enabled=true"
})
class DatabaseInfrastructureTest extends DatabaseTestContainers {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void flywayCreatesSchemaAndMongoConnectionIsAvailable() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains("lessons", "categories", "users", "lesson_number_sequence");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(mongoTemplate.executeCommand(new Document("ping", 1)).getDouble("ok")).isEqualTo(1.0);
    }
}
