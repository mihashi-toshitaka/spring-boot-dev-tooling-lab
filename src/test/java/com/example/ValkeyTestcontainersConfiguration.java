package com.example;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class ValkeyTestcontainersConfiguration {

    private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:8.1.9-alpine");
    private static final int VALKEY_PORT = 6379;

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> valkeyContainer() {
        return new GenericContainer<>(VALKEY_IMAGE).withExposedPorts(VALKEY_PORT);
    }
}
