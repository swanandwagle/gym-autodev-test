package com.studio.booking.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void ac7_canConnectToPostgresContainer() throws Exception {
        assertThat(postgres.isRunning()).isTrue();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(conn.isValid(5)).isTrue();
        }
    }

    @Test
    void ac7_postgresVersionIs16() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT version()");
            rs.next();
            String version = rs.getString(1);
            assertThat(version).contains("PostgreSQL 16");
        }
    }
}
