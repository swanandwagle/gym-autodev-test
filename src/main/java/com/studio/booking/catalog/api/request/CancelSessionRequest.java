package com.studio.booking.catalog.api.request;

import jakarta.validation.constraints.Size;

public record CancelSessionRequest(
        @Size(max = 255, message = "Cancel reason must not exceed 255 characters")
        String reason
) {}
