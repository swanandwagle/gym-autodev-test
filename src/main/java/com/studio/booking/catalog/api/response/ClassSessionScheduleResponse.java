package com.studio.booking.catalog.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ClassSessionScheduleResponse(
        UUID id,
        UUID classTypeId,
        UUID instructorId,
        UUID roomId,
        @JsonProperty("startsAt")
        Instant startsAt,
        @JsonProperty("endsAt")
        Instant endsAt,
        int capacity,
        int bookedCount,
        int availableSpots,
        int waitlistCount,
        String status
) {}
