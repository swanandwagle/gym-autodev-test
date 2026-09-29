package com.studio.booking.catalog.application;

import com.studio.booking.booking.infrastructure.WaitlistCountDto;
import com.studio.booking.booking.infrastructure.WaitlistEntryRepository;
import com.studio.booking.catalog.api.request.BrowseSessionsRequest;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.FieldError;
import com.studio.booking.shared.time.StudioTimeZone;
import com.studio.booking.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BrowseSessionsService {

    private final ClassSessionRepository sessionRepository;
    private final WaitlistEntryRepository waitlistRepository;
    private final StudioTimeZone studioTimeZone;
    private final Clock clock;

    public BrowseSessionsService(
            ClassSessionRepository sessionRepository,
            WaitlistEntryRepository waitlistRepository,
            StudioTimeZone studioTimeZone,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.waitlistRepository = waitlistRepository;
        this.studioTimeZone = studioTimeZone;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassSessionScheduleResponse> browse(BrowseSessionsRequest request) {
        validateRequest(request);

        Instant now = clock.instant();
        Instant from = request.getFrom();
        Instant to = request.getTo();

        // Default: from = now, to = now + 7 days (only if ALL time parameters are absent)
        if (from == null && to == null && request.getDate() == null) {
            from = now;
            to = now.plusSeconds(7 * 86400);
        }

        // Date shorthand takes precedence if provided
        if (request.getDate() != null) {
            ZoneId zoneId = studioTimeZone.zoneId();
            LocalDate date = LocalDate.parse(request.getDate());
            from = date.atStartOfDay(zoneId).toInstant();
            to = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        }

        String status = request.getStatus() != null ? request.getStatus() : "SCHEDULED";
        boolean availableOnly = request.getAvailableOnly() != null && request.getAvailableOnly();

        Sort.Direction direction = Sort.Direction.fromString(request.sortDirection().toUpperCase());
        PageRequest pageable = PageRequest.of(
            request.getPage(),
            request.getSize(),
            Sort.by(direction, request.sortField())
        );

        Page<ClassSession> sessionsPage = sessionRepository.findSessionsWithFilters(
            from,
            to,
            request.getClassTypeId(),
            request.getInstructorId(),
            request.getRoomId(),
            status,
            availableOnly,
            pageable
        );

        List<ClassSession> sessions = sessionsPage.getContent();

        // Fetch waitlist counts in a single query
        List<WaitlistCountDto> waitlistCounts = sessions.isEmpty()
            ? List.of()
            : waitlistRepository.countWaitingBySessionIds(
                sessions.stream().map(ClassSession::getId).collect(Collectors.toList())
            );

        Map<java.util.UUID, Long> waitlistMap = waitlistCounts.stream()
            .collect(Collectors.toMap(WaitlistCountDto::sessionId, WaitlistCountDto::count));

        // Convert to response DTOs
        List<ClassSessionScheduleResponse> responses = sessions.stream()
            .map(s -> new ClassSessionScheduleResponse(
                s.getId(),
                s.getClassTypeId(),
                s.getInstructorId(),
                s.getRoomId(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getCapacity(),
                s.getBookedCount(),
                s.getCapacity() - s.getBookedCount(),
                waitlistMap.getOrDefault(s.getId(), 0L).intValue(),
                s.getStatus()
            ))
            .collect(Collectors.toList());

        return PageResponse.of(responses, request.getPage(), request.getSize(), sessionsPage.getTotalElements());
    }

    private void validateRequest(BrowseSessionsRequest request) {
        // Date cannot be combined with from or to
        if (request.getDate() != null && (request.getFrom() != null || request.getTo() != null)) {
            throw new ApiException(
                ErrorCode.INVALID_RANGE,
                "date parameter cannot be combined with from or to parameters"
            );
        }

        // from and to must be both provided or both absent
        boolean hasFrom = request.getFrom() != null;
        boolean hasTo = request.getTo() != null;
        if (hasFrom != hasTo) {
            throw new ApiException(
                ErrorCode.INVALID_RANGE,
                "from and to must both be provided or both be absent"
            );
        }

        // Validate from/to if both provided
        if (hasFrom && hasTo) {
            if (request.getTo().isBefore(request.getFrom()) || request.getTo().equals(request.getFrom())) {
                throw new ApiException(
                    ErrorCode.INVALID_RANGE,
                    "to must be after from"
                );
            }

            // Check 92-day max span
            long spanSeconds = request.getTo().getEpochSecond() - request.getFrom().getEpochSecond();
            long maxSpanSeconds = 92 * 86400L;
            if (spanSeconds > maxSpanSeconds) {
                throw new ApiException(
                    ErrorCode.OUT_OF_RANGE,
                    "Query span cannot exceed 92 days"
                );
            }
        }

        // Validate sort field
        if (request.getSort() != null && !request.getSort().isBlank()) {
            String sortField = request.sortField();
            if (!sortField.equals("startsAt")) {
                throw ApiException.validationFailed(
                    "Request validation failed. See errors.",
                    List.of(FieldError.of(
                        "sort",
                        "INVALID_ENUM",
                        "Invalid sort field '" + sortField + "'. Permitted fields: startsAt",
                        sortField
                    ))
                );
            }
        }

        // Validate pagination
        if (request.getPage() < 0) {
            throw new ApiException(
                ErrorCode.INVALID_PAGINATION,
                "page must be non-negative"
            );
        }
        if (request.getSize() < 1 || request.getSize() > 100) {
            throw new ApiException(
                ErrorCode.INVALID_PAGINATION,
                "size must be between 1 and 100"
            );
        }
    }
}
