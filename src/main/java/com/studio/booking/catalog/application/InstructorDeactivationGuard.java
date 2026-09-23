package com.studio.booking.catalog.application;

import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class InstructorDeactivationGuard {

    private final InstructorRepository instructorRepository;
    private final Clock clock;

    public InstructorDeactivationGuard(InstructorRepository instructorRepository, Clock clock) {
        this.instructorRepository = instructorRepository;
        this.clock = clock;
    }

    public void checkCanDeactivate(UUID instructorId) {
        Instant now = clock.instant();
        List<ClassSession> futureSessions = instructorRepository.findFutureScheduledSessions(instructorId, now);

        if (!futureSessions.isEmpty()) {
            ClassSession earliest = futureSessions.get(0);
            throw new ApiException(ErrorCode.INSTRUCTOR_HAS_FUTURE_SESSIONS,
                    String.format("Instructor has %d future scheduled session(s). Earliest starts at %s.",
                            futureSessions.size(), earliest.getStartsAt()));
        }
    }
}
