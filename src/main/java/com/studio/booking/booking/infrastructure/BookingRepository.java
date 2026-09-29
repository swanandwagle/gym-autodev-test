package com.studio.booking.booking.infrastructure;

import com.studio.booking.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
        SELECT b FROM Booking b
        WHERE b.sessionId = :sessionId AND b.status = 'BOOKED'
        ORDER BY b.createdAt ASC
    """)
    List<Booking> findBookedBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.memberId = :memberId AND b.idempotencyKey = :idempotencyKey
    """)
    Optional<Booking> findByMemberIdAndIdempotencyKey(
        @Param("memberId") UUID memberId,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE Booking b
        SET b.idempotencyKey = :idempotencyKey,
            b.idempotencyResponseBody = :responseBody
        WHERE b.id = :bookingId
    """)
    void updateIdempotencyFields(
        @Param("bookingId") UUID bookingId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("responseBody") String responseBody
    );
}
