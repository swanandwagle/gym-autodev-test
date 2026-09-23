package com.studio.booking.catalog.application;

import com.studio.booking.booking.infrastructure.WaitlistCountDto;
import com.studio.booking.booking.infrastructure.WaitlistEntryRepository;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for instructor teaching schedule queries.
 * Supports filtering by date range and session status.
 */

@Service
public class InstructorScheduleService {

    private final ClassSessionRepository classSessionRepository;
    private final InstructorRepository instructorRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final Clock clock;

    public InstructorScheduleService(
            ClassSessionRepository classSessionRepository,
            InstructorRepository instructorRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            Clock clock) {
        this.classSessionRepository = classSessionRepository;
        this.instructorRepository = instructorRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassSessionScheduleResponse> getSchedule(
            UUID instructorId,
            Instant from,
            Instant to,
            String status) {

        // AC-7: Verify instructor exists
        if (!instructorRepository.existsById(instructorId)) {
            throw new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND, "No instructor exists for the given ID");
        }

        // Parse status filter
        List<String> statuses = parseStatuses(status);

        // Fetch sessions: AC-1, AC-2, AC-3 (SCHEDULED only by default)
        List<ClassSession> sessions = classSessionRepository.findInstructorSessionsByDateRange(
                instructorId,
                from,
                to,
                statuses
        );

        // Batch fetch waitlist counts to avoid N+1
        List<UUID> sessionIds = sessions.stream().map(ClassSession::getId).toList();
        Map<UUID, Long> waitlistCountMap = waitlistEntryRepository.countWaitingBySessionIds(sessionIds)
                .stream()
                .collect(Collectors.toMap(
                        WaitlistCountDto::sessionId,
                        WaitlistCountDto::count
                ));

        // Convert to response DTOs with calculated fields
        List<ClassSessionScheduleResponse> responses = sessions.stream()
                .map(session -> toResponse(session, waitlistCountMap))
                .toList();

        // Return paginated response
        return PageResponse.of(responses, 0, responses.size(), responses.size());
    }

    private List<String> parseStatuses(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return List.of("SCHEDULED");
        }
        if ("CANCELLED".equalsIgnoreCase(statusParam)) {
            return List.of("CANCELLED");
        }
        if ("SCHEDULED,COMPLETED".equalsIgnoreCase(statusParam) || "COMPLETED,SCHEDULED".equalsIgnoreCase(statusParam)) {
            return List.of("SCHEDULED", "COMPLETED");
        }
        return List.of(statusParam);
    }

    private ClassSessionScheduleResponse toResponse(ClassSession session, Map<UUID, Long> waitlistCountMap) {
        int availableSpots = session.getCapacity() - session.getBookedCount();
        int waitlistCount = (int) waitlistCountMap.getOrDefault(session.getId(), 0L);

        return new ClassSessionScheduleResponse(
                session.getId(),
                session.getClassTypeId(),
                session.getInstructorId(),
                session.getRoomId(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getCapacity(),
                session.getBookedCount(),
                availableSpots,
                waitlistCount,
                session.getStatus()
        );
    }
}
