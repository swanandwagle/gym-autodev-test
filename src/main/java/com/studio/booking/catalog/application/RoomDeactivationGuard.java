package com.studio.booking.catalog.application;

import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.RoomRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class RoomDeactivationGuard {

    private final RoomRepository roomRepository;
    private final Clock clock;

    public RoomDeactivationGuard(RoomRepository roomRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    public void checkCanDeactivate(UUID roomId) {
        Instant now = clock.instant();
        List<ClassSession> futureSessions = roomRepository.findFutureScheduledSessions(roomId, now);

        if (!futureSessions.isEmpty()) {
            ClassSession earliest = futureSessions.get(0);
            throw new ApiException(ErrorCode.ROOM_HAS_FUTURE_SESSIONS,
                    String.format("Room has %d future scheduled session(s). Earliest starts at %s.",
                            futureSessions.size(), earliest.getStartsAt()));
        }
    }
}
