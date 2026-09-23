package com.studio.booking.catalog.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record ClassTypeResponse(
        UUID id,
        String name,
        String description,
        @JsonProperty("durationMinutes")
        int durationMinutes,
        @JsonProperty("defaultCapacity")
        int defaultCapacity,
        boolean active,
        long version,
        @JsonProperty("createdAt")
        Instant createdAt,
        @JsonProperty("updatedAt")
        Instant updatedAt
) {}
