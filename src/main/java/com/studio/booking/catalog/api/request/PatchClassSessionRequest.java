package com.studio.booking.catalog.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PatchClassSessionRequest(
        Instant startsAt,

        @Min(value = 1, message = "durationMinutes must be at least 1")
        @Max(value = 480, message = "durationMinutes must not exceed 480")
        Integer durationMinutes,

        UUID instructorId,

        UUID roomId,

        @Min(value = 1, message = "capacity must be at least 1")
        @Max(value = 500, message = "capacity must not exceed 500")
        Integer capacity,

        Long version
) {
    public Optional<Instant> getStartsAt() {
        return Optional.ofNullable(startsAt);
    }

    public Optional<Integer> getDurationMinutes() {
        return Optional.ofNullable(durationMinutes);
    }

    public Optional<UUID> getInstructorId() {
        return Optional.ofNullable(instructorId);
    }

    public Optional<UUID> getRoomId() {
        return Optional.ofNullable(roomId);
    }

    public Optional<Integer> getCapacity() {
        return Optional.ofNullable(capacity);
    }

    public Optional<Long> getVersion() {
        return Optional.ofNullable(version);
    }

    public boolean hasAnyUpdate() {
        return startsAt != null || durationMinutes != null ||
               instructorId != null || roomId != null || capacity != null;
    }
}
