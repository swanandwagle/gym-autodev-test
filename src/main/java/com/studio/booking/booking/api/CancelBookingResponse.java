package com.studio.booking.booking.api;

import java.time.Instant;
import java.util.UUID;

public record CancelBookingResponse(
    UUID bookingId,
    String status,
    Instant cancelledAt,
    String cancellationType,
    boolean creditRefunded,
    UUID promotedWaitlistEntryId
) {}
