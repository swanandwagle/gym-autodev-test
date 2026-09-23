package com.studio.booking.catalog.application;

import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class FutureSessionCommitmentChecker {

    private final ClassSessionRepository classSessionRepository;
    private final Clock clock;

    public FutureSessionCommitmentChecker(ClassSessionRepository classSessionRepository, Clock clock) {
        this.classSessionRepository = classSessionRepository;
        this.clock = clock;
    }

    public FutureCommitmentResult forInstructor(UUID instructorId) {
        Instant now = clock.instant();
        List<ClassSession> allInstructorSessions = classSessionRepository.findByInstructorId(instructorId);

        List<ClassSession> futureSessions = allInstructorSessions.stream()
                .filter(s -> s.getStartsAt().isAfter(now))
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .sorted((a, b) -> a.getStartsAt().compareTo(b.getStartsAt()))
                .toList();

        return buildResult(futureSessions);
    }

    public FutureCommitmentResult forRoom(UUID roomId) {
        Instant now = clock.instant();
        List<ClassSession> allRoomSessions = classSessionRepository.findByRoomId(roomId);

        List<ClassSession> futureSessions = allRoomSessions.stream()
                .filter(s -> s.getStartsAt().isAfter(now))
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .sorted((a, b) -> a.getStartsAt().compareTo(b.getStartsAt()))
                .toList();

        return buildResult(futureSessions);
    }

    public FutureCommitmentResult forRoomExceedingCapacity(UUID roomId, int proposedCapacity) {
        Instant now = clock.instant();
        List<ClassSession> allRoomSessions = classSessionRepository.findByRoomId(roomId);

        List<ClassSession> futureSessions = allRoomSessions.stream()
                .filter(s -> s.getStartsAt().isAfter(now))
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .filter(s -> s.getCapacity() > proposedCapacity)
                .sorted((a, b) -> a.getStartsAt().compareTo(b.getStartsAt()))
                .toList();

        return buildResult(futureSessions);
    }

    private FutureCommitmentResult buildResult(List<ClassSession> futureSessions) {
        long count = futureSessions.size();
        Instant earliestStart = futureSessions.isEmpty() ? null : futureSessions.get(0).getStartsAt();
        Integer firstSessionCapacity = futureSessions.isEmpty() ? null : futureSessions.get(0).getCapacity();
        List<UUID> sessionIds = futureSessions.stream()
                .limit(10)
                .map(ClassSession::getId)
                .toList();

        return new FutureCommitmentResult(count, earliestStart, sessionIds, firstSessionCapacity);
    }
}
