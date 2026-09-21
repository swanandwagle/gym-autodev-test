package com.studio.booking.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-2: Clock is injectable; test replaces it with Clock.fixed and
 * observes production code reading the frozen instant.
 */
class ClockProviderTest {

    @Test
    void clockFixed_observesFrozenInstant() {
        Instant frozen = Instant.parse("2026-01-15T10:00:00Z");
        Clock fixedClock = Clock.fixed(frozen, ZoneOffset.UTC);

        ClockProvider clockProvider = new ClockProvider(fixedClock);

        assertThat(clockProvider.now()).isEqualTo(frozen);
        assertThat(clockProvider.now()).isEqualTo(frozen);
    }

    @Test
    void clockFixed_doesNotAdvance() {
        Instant frozen = Instant.parse("2026-06-01T08:30:00Z");
        Clock fixedClock = Clock.fixed(frozen, ZoneOffset.UTC);
        ClockProvider clockProvider = new ClockProvider(fixedClock);

        Instant first = clockProvider.now();
        Instant second = clockProvider.now();

        assertThat(first).isEqualTo(second).isEqualTo(frozen);
    }
}
