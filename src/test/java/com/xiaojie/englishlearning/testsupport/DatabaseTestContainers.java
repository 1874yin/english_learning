package com.xiaojie.englishlearning.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.mysql.MySQLContainer;

public abstract class DatabaseTestContainers {

    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    protected static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    static {
        MYSQL.start();
        MONGODB.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }
}
