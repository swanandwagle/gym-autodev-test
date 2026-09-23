package com.studio.booking.catalog.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Optional;

public record PatchRoomRequest(
        @Min(value = 1, message = "capacity must be at least 1")
        @Max(value = 500, message = "capacity must not exceed 500")
        Integer capacity,

        Long version
) {
    public Optional<Integer> getCapacity() {
        return Optional.ofNullable(capacity);
    }

    public Optional<Long> getVersion() {
        return Optional.ofNullable(version);
    }

    public boolean hasAnyUpdate() {
        return capacity != null;
    }
}
