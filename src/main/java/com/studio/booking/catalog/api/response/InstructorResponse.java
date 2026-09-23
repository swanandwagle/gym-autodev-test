package com.studio.booking.catalog.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstructorResponse(
        UUID id,
        String email,
        String name,
        String bio,
        boolean active,
        List<String> specialties,
        long version,
        @JsonProperty("createdAt")
        Instant createdAt,
        @JsonProperty("updatedAt")
        Instant updatedAt
) {}
