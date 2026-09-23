package com.studio.booking.catalog.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String name,
        int capacity,
        boolean active,
        long version,
        @JsonProperty("createdAt")
        Instant createdAt,
        @JsonProperty("updatedAt")
        Instant updatedAt
) {}
