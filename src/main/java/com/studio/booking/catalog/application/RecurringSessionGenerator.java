package com.studio.booking.catalog.application;

import com.studio.booking.catalog.api.request.CreateRecurringSessionsRequest;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.api.response.CreateRecurringSessionsResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.ClassType;
import com.studio.booking.catalog.domain.DayOfWeek;
import com.studio.booking.catalog.domain.Instructor;
import com.studio.booking.catalog.domain.RecurrenceConflict;
import com.studio.booking.catalog.domain.Room;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.catalog.infrastructure.ClassTypeRepository;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.catalog.infrastructure.RoomRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.time.StudioTimeZone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek as JavaDayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecurringSessionGenerator {

    private final ClassSessionRepository sessionRepository;
    private final ClassTypeRepository classTypeRepository;
    private final InstructorRepository instructorRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;
    private final StudioTimeZone studioTimeZone;

    public RecurringSessionGenerator(ClassSessionRepository sessionRepository,
                                   ClassTypeRepository classTypeRepository,
                                   InstructorRepository instructorRepository,
                                   RoomRepository roomRepository,
                                   Clock clock,
                                   StudioTimeZone studioTimeZone) {
        this.sessionRepository = sessionRepository;
        this.classTypeRepository = classTypeRepository;
        this.instructorRepository = instructorRepository;
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.studioTimeZone = studioTimeZone;
    }

    @Transactional
    public CreateRecurringSessionsResponse generate(CreateRecurringSessionsRequest request, ZoneId overrideTimeZone) {
        ZoneId zoneId = overrideTimeZone != null ? overrideTimeZone : studioTimeZone.zoneId();
        Instant now = clock.instant();
        LocalDate today = now.atZone(zoneId).toLocalDate();

        validateRequest(request, today);

        ClassType classType = classTypeRepository.findById(request.classTypeId())
                .orElseThrow(() -> new ApiException(ErrorCode.CLASS_TYPE_NOT_FOUND,
                        "Class type not found"));

        if (!classType.isActive()) {
            throw new ApiException(ErrorCode.CLASS_TYPE_INACTIVE,
                    "Class type is inactive");
        }

        Instructor instructor = instructorRepository.findById(request.instructorId())
                .orElseThrow(() -> new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND,
                        "Instructor not found"));

        if (!instructor.isActive()) {
            throw new ApiException(ErrorCode.INSTRUCTOR_INACTIVE,
                    "Instructor is inactive");
        }

        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND,
                        "Room not found"));

        if (!room.isActive()) {
            throw new ApiException(ErrorCode.ROOM_INACTIVE,
                    "Room is inactive");
        }

        int effectiveDuration = request.durationMinutes() != null
                ? request.durationMinutes()
                : classType.getDurationMinutes();

        int effectiveCapacity = request.capacity() != null
                ? request.capacity()
                : classType.getDefaultCapacity();

        if (effectiveCapacity > room.getCapacity()) {
            throw new ApiException(ErrorCode.SESSION_CAPACITY_EXCEEDS_ROOM,
                    "Session capacity exceeds room maximum capacity");
        }

        Set<String> daysSet = new HashSet<>(request.daysOfWeek());
        if (daysSet.size() != request.daysOfWeek().size()) {
            throw new ApiException(ErrorCode.INVALID_FORMAT,
                    "daysOfWeek contains duplicate entries");
        }

        List<DayOfWeek> daysOfWeek = request.daysOfWeek().stream()
                .map(d -> {
                    try {
                        return DayOfWeek.valueOf(d.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new ApiException(ErrorCode.INVALID_FORMAT,
                                "Invalid day of week: " + d);
                    }
                })
                .collect(Collectors.toList());

        List<LocalDateTime> occurrences = generateOccurrences(
                request.fromDate(), request.toDate(), daysOfWeek, request.startTimeLocal(), zoneId, now);

        if (occurrences.isEmpty()) {
            throw new ApiException(ErrorCode.RECURRENCE_EMPTY,
                    "Recurrence produces no future occurrences");
        }

        UUID recurrenceId = UUID.randomUUID();
        List<ClassSessionScheduleResponse> created = new ArrayList<>();
        List<RecurrenceConflict> conflicts = new ArrayList<>();

        if ("FAIL".equalsIgnoreCase(request.onConflict())) {
            List<RecurrenceConflict> allConflicts = detectConflicts(occurrences, request.instructorId(), request.roomId(), effectiveDuration, zoneId);
            if (!allConflicts.isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Conflicts detected: " + allConflicts.size());
            }
        }

        int skipped = 0;
        for (int i = 0; i < occurrences.size(); i++) {
            LocalDateTime localDt = occurrences.get(i);
            Instant startsAt = localDt.atZone(zoneId).toInstant();
            Instant endsAt = startsAt.plusSeconds((long) effectiveDuration * 60);

            List<RecurrenceConflict> sessionConflicts = detectConflicts(
                    List.of(localDt), request.instructorId(), request.roomId(), effectiveDuration, zoneId);

            if (!sessionConflicts.isEmpty()) {
                if ("SKIP".equalsIgnoreCase(request.onConflict())) {
                    skipped++;
                    for (RecurrenceConflict conflict : sessionConflicts) {
                        conflicts.add(new RecurrenceConflict(i, conflict.type(), conflict.conflictingSessionId()));
                    }
                    continue;
                }
            }

            ClassSession session = new ClassSession(
                    request.classTypeId(),
                    request.instructorId(),
                    request.roomId(),
                    startsAt,
                    endsAt,
                    effectiveCapacity,
                    recurrenceId,
                    clock);

            try {
                ClassSession saved = sessionRepository.save(session);
                created.add(toResponse(saved));
            } catch (Exception e) {
                if ("SKIP".equalsIgnoreCase(request.onConflict())) {
                    skipped++;
                } else {
                    throw e;
                }
            }
        }

        return new CreateRecurringSessionsResponse(
                recurrenceId,
                created,
                skipped > 0 ? skipped : null,
                conflicts.isEmpty() ? null : conflicts);
    }

    private void validateRequest(CreateRecurringSessionsRequest request, LocalDate today) {
        if (request.toDate().isBefore(request.fromDate()) || request.toDate().isEqual(request.fromDate())) {
            throw new ApiException(ErrorCode.INVALID_RANGE,
                    "toDate must be after fromDate");
        }

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(request.fromDate(), request.toDate());
        if (daysBetween > 365) {
            throw new ApiException(ErrorCode.OUT_OF_RANGE,
                    "Date range cannot span more than 365 days");
        }

        if (request.fromDate().isBefore(today)) {
            throw new ApiException(ErrorCode.OUT_OF_RANGE,
                    "fromDate cannot be before today");
        }
    }

    private List<LocalDateTime> generateOccurrences(LocalDate fromDate, LocalDate toDate, List<DayOfWeek> daysOfWeek,
                                                     LocalTime startTime, ZoneId zoneId, Instant now) {
        List<LocalDateTime> occurrences = new ArrayList<>();
        LocalDate current = fromDate;

        Set<JavaDayOfWeek> targetDays = daysOfWeek.stream()
                .map(DayOfWeek::toJavaTime)
                .collect(Collectors.toSet());

        while (!current.isAfter(toDate)) {
            if (targetDays.contains(current.getDayOfWeek())) {
                LocalDateTime localDt = LocalDateTime.of(current, startTime);
                Instant instant = localDt.atZone(zoneId).toInstant();

                if (instant.isAfter(now)) {
                    occurrences.add(localDt);
                }
            }
            current = current.plusDays(1);
        }

        return occurrences;
    }

    private List<RecurrenceConflict> detectConflicts(List<LocalDateTime> occurrences, UUID instructorId, UUID roomId,
                                                     int durationMinutes, ZoneId zoneId) {
        List<RecurrenceConflict> conflicts = new ArrayList<>();

        for (int i = 0; i < occurrences.size(); i++) {
            LocalDateTime localDt = occurrences.get(i);
            Instant startsAt = localDt.atZone(zoneId).toInstant();
            Instant endsAt = startsAt.plusSeconds((long) durationMinutes * 60);

            List<ClassSession> instructorConflicts = sessionRepository.findOverlappingInstructorSessions(instructorId, startsAt, endsAt);
            for (ClassSession conflict : instructorConflicts) {
                if (!conflict.getStatus().equals("CANCELLED")) {
                    conflicts.add(new RecurrenceConflict(i, "INSTRUCTOR", conflict.getId()));
                }
            }

            List<ClassSession> roomConflicts = sessionRepository.findOverlappingRoomSessions(roomId, startsAt, endsAt);
            for (ClassSession conflict : roomConflicts) {
                if (!conflict.getStatus().equals("CANCELLED")) {
                    conflicts.add(new RecurrenceConflict(i, "ROOM", conflict.getId()));
                }
            }
        }

        return conflicts;
    }

    private ClassSessionScheduleResponse toResponse(ClassSession session) {
        int availableSpots = session.getCapacity() - session.getBookedCount();
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
                0,
                session.getStatus()
        );
    }
}
