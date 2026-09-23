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
}

