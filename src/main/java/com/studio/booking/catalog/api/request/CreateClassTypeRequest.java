package com.studio.booking.catalog.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

public record CreateClassTypeRequest(
        @NotBlank(message = "name is required")
        String name,

        String description,

        @Min(value = 5, message = "durationMinutes must be at least 5")
        @Max(value = 480, message = "durationMinutes must not exceed 480")
        int durationMinutes,

        @Min(value = 1, message = "defaultCapacity must be at least 1")
        @Max(value = 500, message = "defaultCapacity must not exceed 500")
        int defaultCapacity
) {}
