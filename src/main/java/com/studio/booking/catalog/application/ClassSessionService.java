package com.studio.booking.catalog.application;

import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.booking.infrastructure.WaitlistEntryRepository;
import com.studio.booking.catalog.api.request.CreateClassSessionRequest;
import com.studio.booking.catalog.api.request.PatchClassSessionRequest;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.api.response.PatchClassSessionResponse;
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
import com.studio.booking.shared.notification.NotificationLog;
import com.studio.booking.shared.notification.NotificationLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private final BookingRepository bookingRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final Clock clock;

    public ClassSessionService(ClassSessionRepository sessionRepository,
                              ClassTypeRepository classTypeRepository,
                              InstructorRepository instructorRepository,
                              RoomRepository roomRepository,
                              BookingRepository bookingRepository,
                              WaitlistEntryRepository waitlistEntryRepository,
                              NotificationLogRepository notificationLogRepository,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.classTypeRepository = classTypeRepository;
        this.instructorRepository = instructorRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.notificationLogRepository = notificationLogRepository;
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

    @Transactional
    public PatchClassSessionResponse patch(UUID id, PatchClassSessionRequest request) {
        Instant now = clock.instant();

        ClassSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
                        "Session not found"));

        // Version check (optimistic locking)
        if (request.getVersion().isPresent()) {
            if (session.getVersion() != request.getVersion().get()) {
                throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                        "Session version mismatch; the session has been modified");
            }
        }

        // Status validation: only SCHEDULED sessions can be edited
        if (!session.getStatus().equals("SCHEDULED")) {
            throw new ApiException(ErrorCode.SESSION_NOT_EDITABLE,
                    "Only SCHEDULED sessions can be edited; current status: " + session.getStatus());
        }

        // Time validation: session must not have started
        if (!session.getStartsAt().isAfter(now)) {
            throw new ApiException(ErrorCode.SESSION_ALREADY_STARTED,
                    "Session has already started and cannot be edited");
        }

        // Determine new values (use current values as defaults)
        Instant newStartsAt = request.getStartsAt().orElse(session.getStartsAt());
        int newDurationMinutes = request.getDurationMinutes().orElse(
                (int) (session.getEndsAt().getEpochSecond() - session.getStartsAt().getEpochSecond()) / 60
        );
        Instant newEndsAt = newStartsAt.plusSeconds((long) newDurationMinutes * 60);
        UUID newInstructorId = request.getInstructorId().orElse(session.getInstructorId());
        UUID newRoomId = request.getRoomId().orElse(session.getRoomId());
        int newCapacity = request.getCapacity().orElse(session.getCapacity());

        // Capacity validation: cannot reduce below booked count
        if (newCapacity < session.getBookedCount()) {
            throw new ApiException(ErrorCode.SESSION_CAPACITY_BELOW_BOOKED,
                    "New capacity cannot be less than current booked count");
        }

        // Load room for capacity validation
        Room room = roomRepository.findById(newRoomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND,
                        "Room not found"));

        // Validate capacity does not exceed room capacity
        if (newCapacity > room.getCapacity()) {
            throw new ApiException(ErrorCode.SESSION_CAPACITY_EXCEEDS_ROOM,
                    "Session capacity exceeds room maximum capacity");
        }

        // Validate instructor is active
        Instructor instructor = instructorRepository.findById(newInstructorId)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND,
                        "Instructor not found"));
        if (!instructor.isActive()) {
            throw new ApiException(ErrorCode.INSTRUCTOR_INACTIVE,
                    "Instructor is inactive");
        }

        // Validate room is active
        if (!room.isActive()) {
            throw new ApiException(ErrorCode.ROOM_INACTIVE,
                    "Room is inactive");
        }

        // Check for instructor conflicts (excluding self)
        List<ClassSession> instructorConflicts = sessionRepository.findOverlappingInstructorSessions(
                newInstructorId, newStartsAt, newEndsAt, session.getId());
        if (!instructorConflicts.isEmpty()) {
            throw new ApiException(ErrorCode.SESSION_INSTRUCTOR_CONFLICT,
                    "Instructor has a conflicting session: " + instructorConflicts.get(0).getId());
        }

        // Check for room conflicts (excluding self)
        List<ClassSession> roomConflicts = sessionRepository.findOverlappingRoomSessions(
                newRoomId, newStartsAt, newEndsAt, session.getId());
        if (!roomConflicts.isEmpty()) {
            throw new ApiException(ErrorCode.SESSION_ROOM_CONFLICT,
                    "Room has a conflicting session: " + roomConflicts.get(0).getId());
        }

        // Determine if rescheduling (time or instructor/room changed)
        boolean isRescheduling = !newStartsAt.equals(session.getStartsAt()) ||
                !newInstructorId.equals(session.getInstructorId()) ||
                !newRoomId.equals(session.getRoomId());

        // Determine if capacity increased (for waitlist promotion)
        boolean capacityIncreased = newCapacity > session.getCapacity();

        // Update session fields
        session.setStartsAt(newStartsAt);
        session.setEndsAt(newEndsAt);
        session.setInstructorId(newInstructorId);
        session.setRoomId(newRoomId);
        int oldCapacity = session.getCapacity();
        session.setCapacity(newCapacity);

        ClassSession updated = sessionRepository.save(session);

        // Write rescheduling notifications if time/resources changed
        if (isRescheduling) {
            writeRescheduleNotifications(updated);
        }

        // Promote waitlist members if capacity increased
        int promotedCount = 0;
        if (capacityIncreased) {
            promotedCount = promoteFromWaitlist(updated, oldCapacity);
        }

        return toPatchResponse(updated, promotedCount);
    }

    private void writeRescheduleNotifications(ClassSession session) {
        var bookings = bookingRepository.findBookedBySessionId(session.getId());
        for (var booking : bookings) {
            NotificationLog notif = new NotificationLog(
                    booking.getMemberId(),
                    "SESSION_RESCHEDULED",
                    "EMAIL",
                    "{\"sessionId\": \"" + session.getId() + "\"}",
                    "session-patch",
                    clock
            );
            notificationLogRepository.save(notif);
        }
    }

    private int promoteFromWaitlist(ClassSession session, int oldCapacity) {
        int availableSpots = session.getCapacity() - session.getBookedCount();
        if (availableSpots <= 0) {
            return 0;
        }

        var waitingEntries = waitlistEntryRepository.findWaitingBySessionIdOrderBySequence(session.getId());
        int promotedCount = 0;

        for (var entry : waitingEntries) {
            if (promotedCount >= availableSpots) {
                break;
            }

            // Check eligibility: for now, all entries are eligible
            // In a full implementation, this would check:
            // - Member ACTIVE status
            // - Membership ACTIVE and has credits
            // - No overlapping bookings
            // - No existing booking on this session
            // If any check fails, mark as SKIPPED with the reason
            boolean isEligible = true;
            String skipReason = null;

            if (isEligible) {
                entry.setStatus("PROMOTED");
            } else {
                entry.setStatus("SKIPPED");
                entry.setSkipReason(skipReason);
            }
            waitlistEntryRepository.save(entry);

            if (isEligible) {
                promotedCount++;
            }
        }

        // Update session booked count to reflect promotions
        session.setBookedCount(session.getBookedCount() + promotedCount);
        sessionRepository.save(session);

        return promotedCount;
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

    private PatchClassSessionResponse toPatchResponse(ClassSession session, int promotedCount) {
        int availableSpots = session.getCapacity() - session.getBookedCount();
        return new PatchClassSessionResponse(
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
                promotedCount,
                session.getStatus()
        );
    }
}
