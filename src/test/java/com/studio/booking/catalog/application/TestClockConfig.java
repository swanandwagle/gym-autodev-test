package com.studio.booking.catalog.application;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@TestConfiguration
public class TestClockConfig {

    private static final ThreadLocal<Instant> fixedInstant = new ThreadLocal<>();

    @Bean
    @Primary
    public Clock testClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                Instant fixed = fixedInstant.get();
                return fixed != null ? fixed : Instant.now();
            }
        };
    }

    public static void setFixedTime(Instant instant) {
        fixedInstant.set(instant);
    }

    public static void clearFixedTime() {
        fixedInstant.remove();
    }
}
