package com.studio.booking.catalog.application;

import com.studio.booking.catalog.api.request.CreateClassSessionRequest;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.ClassType;
import com.studio.booking.catalog.domain.Instructor;
import com.studio.booking.catalog.domain.Room;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.catalog.infrastructure.ClassTypeRepository;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.catalog.infrastructure.RoomRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ClassSessionService {

    private final ClassSessionRepository sessionRepository;
    private final ClassTypeRepository classTypeRepository;
    private final InstructorRepository instructorRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;

    public ClassSessionService(ClassSessionRepository sessionRepository,
                              ClassTypeRepository classTypeRepository,
                              InstructorRepository instructorRepository,
                              RoomRepository roomRepository,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.classTypeRepository = classTypeRepository;
        this.instructorRepository = instructorRepository;
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    @Transactional
    public ClassSessionScheduleResponse create(CreateClassSessionRequest request) {
        Instant now = clock.instant();

        // Validate startsAt is in the future
        if (!request.startsAt().isAfter(now)) {
            throw new ApiException(ErrorCode.FUTURE_REQUIRED,
                    "Session start time must be in the future");
        }

        // Validate startsAt is at a minute boundary (zero seconds and nanoseconds)
        long secondsPartOfMinute = request.startsAt().getEpochSecond() % 60;
        int nanos = request.startsAt().getNano();
        if (secondsPartOfMinute != 0 || nanos != 0) {
            throw new ApiException(ErrorCode.INVALID_FORMAT,
                    "Session start time must be at a minute boundary (MM:00:00)");
        }

        // Load and validate class type
        ClassType classType = classTypeRepository.findById(request.classTypeId())
                .orElseThrow(() -> new ApiException(ErrorCode.CLASS_TYPE_NOT_FOUND,
                        "Class type not found"));

        if (!classType.isActive()) {
            throw new ApiException(ErrorCode.CLASS_TYPE_INACTIVE,
                    "Class type is inactive");
        }

        // Load and validate instructor
        Instructor instructor = instructorRepository.findById(request.instructorId())
                .orElseThrow(() -> new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND,
                        "Instructor not found"));

        if (!instructor.isActive()) {
            throw new ApiException(ErrorCode.INSTRUCTOR_INACTIVE,
                    "Instructor is inactive");
        }

        // Load and validate room
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND,
                        "Room not found"));

        if (!room.isActive()) {
            throw new ApiException(ErrorCode.ROOM_INACTIVE,
                    "Room is inactive");
        }

        // Determine effective duration and capacity
        int effectiveDuration = request.durationMinutes() != null
                ? request.durationMinutes()
                : classType.getDurationMinutes();

        int effectiveCapacity = request.capacity() != null
                ? request.capacity()
                : classType.getDefaultCapacity();

        // Calculate endsAt
        Instant endsAt = request.startsAt().plusSeconds((long) effectiveDuration * 60);

        // Validate capacity does not exceed room capacity
        if (effectiveCapacity > room.getCapacity()) {
            throw new ApiException(ErrorCode.SESSION_CAPACITY_EXCEEDS_ROOM,
                    "Session capacity exceeds room maximum capacity");
        }

        // Check for instructor conflicts (using half-open interval [start, end))
        List<ClassSession> instructorConflicts = sessionRepository.findOverlappingInstructorSessions(
                request.instructorId(), request.startsAt(), endsAt);

        if (!instructorConflicts.isEmpty()) {
            throw new ApiException(ErrorCode.SESSION_INSTRUCTOR_CONFLICT,
                    "Instructor has a conflicting session: " + instructorConflicts.get(0).getId());
        }

        // Check for room conflicts (using half-open interval [start, end))
        List<ClassSession> roomConflicts = sessionRepository.findOverlappingRoomSessions(
                request.roomId(), request.startsAt(), endsAt);

        if (!roomConflicts.isEmpty()) {
            throw new ApiException(ErrorCode.SESSION_ROOM_CONFLICT,
                    "Room has a conflicting session: " + roomConflicts.get(0).getId());
        }

        // Create and persist the session
        ClassSession session = new ClassSession(
                request.classTypeId(),
                request.instructorId(),
                request.roomId(),
                request.startsAt(),
                endsAt,
                effectiveCapacity,
                clock
        );

        try {
            ClassSession saved = sessionRepository.save(session);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            if (msg != null && msg.contains("instructor")) {
                throw new ApiException(ErrorCode.SESSION_INSTRUCTOR_CONFLICT,
                        "Instructor has a conflicting session");
            }
            if (msg != null && msg.contains("room")) {
                throw new ApiException(ErrorCode.SESSION_ROOM_CONFLICT,
                        "Room has a conflicting session");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ClassSessionScheduleResponse getById(UUID id) {
        ClassSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
                        "Session not found"));
        return toResponse(session);
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
