package com.studio.booking.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FutureCommitmentResult(
        long count,
        Instant earliestStart,
        List<UUID> sessionIds,
        Integer firstSessionCapacity
) {}
