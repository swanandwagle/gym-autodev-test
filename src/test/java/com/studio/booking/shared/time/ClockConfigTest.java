package com.studio.booking.shared.time;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ClockConfigTest {

    static final Instant FROZEN = Instant.parse("2026-01-15T10:00:00Z");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class FrozenClockConfig {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(FROZEN, ZoneOffset.UTC);
        }
    }

    @Autowired
    Clock clock;

    @Test
    void ac2_clockBeanIsInjectedAndCanBeFrozen() {
        assertThat(clock.instant()).isEqualTo(FROZEN);
    }

    @Test
    void ac2_frozenClockDoesNotAdvance() throws InterruptedException {
        Instant first = clock.instant();
        Thread.sleep(10);
        Instant second = clock.instant();
        assertThat(second).isEqualTo(first);
    }
}
