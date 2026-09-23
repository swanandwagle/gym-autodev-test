package com.studio.booking.catalog.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateClassSessionRequest(
        @NotNull(message = "classTypeId is required")
        UUID classTypeId,

        @NotNull(message = "instructorId is required")
        UUID instructorId,

        @NotNull(message = "roomId is required")
        UUID roomId,

        @NotNull(message = "startsAt is required")
        @JsonProperty("startsAt")
        Instant startsAt,

        Integer durationMinutes,

        Integer capacity
) {}
