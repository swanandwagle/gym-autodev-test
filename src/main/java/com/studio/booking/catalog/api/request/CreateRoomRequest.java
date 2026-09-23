package com.studio.booking.catalog.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(
        @NotBlank(message = "name is required")
        String name,

        @Min(value = 1, message = "capacity must be at least 1")
        @Max(value = 500, message = "capacity must not exceed 500")
        int capacity
) {}
