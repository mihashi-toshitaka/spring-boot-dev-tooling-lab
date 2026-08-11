package com.example;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestcontainersConfiguration {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.6-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("spring_boot_dev_tooling_lab")
                .withUsername("app")
                .withPassword("app");
    }
}
