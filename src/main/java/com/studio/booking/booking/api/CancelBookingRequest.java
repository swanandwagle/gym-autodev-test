package com.studio.booking.booking.api;

import jakarta.validation.constraints.Size;

public record CancelBookingRequest(@Size(max = 255) String reason) {}
