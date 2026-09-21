package com.studio.booking.shared.time;

import com.studio.booking.BookingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BookingApplication.class)
@ActiveProfiles("test")
@Testcontainers
class StudioTimeZoneTest {

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
        registry.add("studio.timezone", () -> "Asia/Kolkata");
    }

    @Autowired
    StudioTimeZone studioTimeZone;

    @Test
    void ac6_timezonePropertyIsBound() {
        assertThat(studioTimeZone.timezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void ac6_zoneIdParsedCorrectly() {
        assertThat(studioTimeZone.zoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}
