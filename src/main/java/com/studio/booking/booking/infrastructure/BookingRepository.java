package com.studio.booking.booking.infrastructure;

import com.studio.booking.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
        SELECT b FROM Booking b
        WHERE b.sessionId = :sessionId AND b.status = 'BOOKED'
        ORDER BY b.createdAt ASC
    """)
    List<Booking> findBookedBySessionId(@Param("sessionId") UUID sessionId);
}
