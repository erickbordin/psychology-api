package com.psychologyapi.psychologyapi.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe um Postgres real via Testcontainers para os testes que tocam o banco.
 *
 * O Flyway roda as migrations de src/main/resources/db/migration no container,
 * entao estes testes tambem validam que as migrations aplicam limpas do zero.
 *
 * O container e um bean: o Spring gerencia o ciclo de vida e o contexto e
 * cacheado entre as classes de teste, ou seja, um unico container por build.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
