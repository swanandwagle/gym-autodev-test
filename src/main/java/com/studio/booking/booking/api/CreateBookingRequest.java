package com.studio.booking.booking.api;

import com.studio.booking.shared.validation.ValidUuid;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
    @NotNull(message = "memberId is required")
    @ValidUuid
    String memberId,

    @NotNull(message = "sessionId is required")
    @ValidUuid
    String sessionId
) {}
