package com.studio.booking.booking.infrastructure;

import java.util.UUID;

public record WaitlistCountDto(UUID sessionId, long count) {}
