package com.studio.booking.catalog.domain;

import java.util.UUID;

public record RecurrenceConflict(
    int occurrenceIndex,
    String type,
    UUID conflictingSessionId
) {
    public RecurrenceConflict {
        if (occurrenceIndex < 0) {
            throw new IllegalArgumentException("occurrenceIndex must be >= 0");
        }
        if (type == null || (!type.equals("INSTRUCTOR") && !type.equals("ROOM"))) {
            throw new IllegalArgumentException("type must be INSTRUCTOR or ROOM");
        }
        if (conflictingSessionId == null) {
            throw new IllegalArgumentException("conflictingSessionId must not be null");
        }
    }
}
