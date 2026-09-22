package com.studio.booking.shared.time;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "studio")
public record StudioTimeZone(String timezone) {

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
