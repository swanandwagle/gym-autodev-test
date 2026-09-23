package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.instructorId = :instructorId
          AND s.startsAt >= :from
          AND s.startsAt < :to
          AND s.status IN (:statuses)
        ORDER BY s.startsAt ASC
    """)
    List<ClassSession> findInstructorSessionsByDateRange(
            @Param("instructorId") UUID instructorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("statuses") List<String> statuses
    );

    @Query("SELECT s FROM ClassSession s WHERE s.instructorId = :instructorId")
    List<ClassSession> findByInstructorId(@Param("instructorId") UUID instructorId);

    @Query("SELECT s FROM ClassSession s WHERE s.roomId = :roomId")
    List<ClassSession> findByRoomId(@Param("roomId") UUID roomId);

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.instructorId = :instructorId
          AND s.startsAt < :endsAt
          AND s.endsAt > :startsAt
          AND s.status != 'CANCELLED'
    """)
    List<ClassSession> findOverlappingInstructorSessions(
            @Param("instructorId") UUID instructorId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.roomId = :roomId
          AND s.startsAt < :endsAt
          AND s.endsAt > :startsAt
          AND s.status != 'CANCELLED'
    """)
    List<ClassSession> findOverlappingRoomSessions(
            @Param("roomId") UUID roomId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.recurrenceId = :recurrenceId
        ORDER BY s.startsAt ASC
    """)
    List<ClassSession> findByRecurrenceIdOrderByStartsAt(@Param("recurrenceId") UUID recurrenceId);
}

