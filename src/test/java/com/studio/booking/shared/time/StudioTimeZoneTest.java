package com.studio.booking.shared.time;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-6: studio.timezone is bound and readable from StudioTimeZone configuration properties.
 */
@SpringBootTest(
        classes = {StudioTimeZoneTest.Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "studio.timezone=Asia/Kolkata"
)
class StudioTimeZoneTest {

    @EnableConfigurationProperties(StudioTimeZone.class)
    static class Config {}

    @Autowired
    private StudioTimeZone studioTimeZone;

    @Test
    void timezonePropertyBound() {
        assertThat(studioTimeZone.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(studioTimeZone.zoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}
