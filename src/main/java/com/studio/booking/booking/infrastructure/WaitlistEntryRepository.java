package com.studio.booking.booking.infrastructure;

import com.studio.booking.booking.domain.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    @Query("""
        SELECT new com.studio.booking.booking.infrastructure.WaitlistCountDto(w.sessionId, COUNT(w))
        FROM WaitlistEntry w
        WHERE w.sessionId IN :sessionIds AND w.status = 'WAITING'
        GROUP BY w.sessionId
    """)
    List<WaitlistCountDto> countWaitingBySessionIds(@Param("sessionIds") List<UUID> sessionIds);

    @Query("""
        SELECT w FROM WaitlistEntry w
        WHERE w.sessionId = :sessionId AND w.status = 'WAITING'
        ORDER BY w.sequenceNo ASC
    """)
    List<WaitlistEntry> findWaitingBySessionIdOrderBySequence(@Param("sessionId") UUID sessionId);
}


