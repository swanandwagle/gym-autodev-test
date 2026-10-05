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
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    interface BookingDetail {
        UUID getId(); UUID getMemberId(); UUID getSessionId(); UUID getMembershipId();
        String getStatus(); String getSource(); Instant getBookedAt(); String getCancellationType();
        Boolean getCreditRefunded(); Instant getCheckedInAt(); String getCheckedInBy();
        Instant getStartsAt(); Instant getEndsAt(); String getClassTypeName(); String getInstructorName(); String getRoomName();
    }

    String DETAIL_SELECT = "SELECT b.id AS id, b.member_id AS \"memberId\", b.session_id AS \"sessionId\", " +
        "b.membership_id AS \"membershipId\", b.status AS status, b.source AS source, b.created_at AS \"bookedAt\", " +
        "b.cancellation_type AS \"cancellationType\", b.credit_refunded AS \"creditRefunded\", " +
        "b.checked_in_at AS \"checkedInAt\", b.checked_in_by AS \"checkedInBy\", s.starts_at AS \"startsAt\", " +
        "s.ends_at AS \"endsAt\", ct.name AS \"classTypeName\", i.name AS \"instructorName\", r.name AS \"roomName\" " +
        "FROM booking b JOIN class_session s ON s.id=b.session_id JOIN class_type ct ON ct.id=s.class_type_id " +
        "JOIN instructor i ON i.id=s.instructor_id JOIN room r ON r.id=s.room_id ";

    @Query(value = DETAIL_SELECT + "WHERE b.id=:id", nativeQuery = true)
    Optional<BookingDetail> findDetailById(@Param("id") UUID id);

    @Query(value = DETAIL_SELECT + "WHERE b.member_id=:memberId " +
        "AND (:statusFilter IS NULL OR b.status = ANY(string_to_array(:statusFilter, ','))) " +
        "AND (:fromTime IS NULL OR s.starts_at >= :fromTime) " +
        "AND (:toTime IS NULL OR s.starts_at < :toTime) " +
        "AND (:upcomingOnly = false OR (b.status='BOOKED' AND s.starts_at > :now))",
        countQuery = "SELECT count(*) FROM booking b JOIN class_session s ON s.id=b.session_id " +
        "WHERE b.member_id=:memberId AND (:statusFilter IS NULL OR b.status = ANY(string_to_array(:statusFilter, ','))) " +
            "AND (:fromTime IS NULL OR s.starts_at >= :fromTime) AND (:toTime IS NULL OR s.starts_at < :toTime) " +
            "AND (:upcomingOnly = false OR (b.status='BOOKED' AND s.starts_at > :now))",
        nativeQuery = true)
    Page<BookingDetail> findHistory(@Param("memberId") UUID memberId, @Param("statusFilter") String statusFilter,
        @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime,
        @Param("upcomingOnly") boolean upcomingOnly, @Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.sessionId = :sessionId AND b.status = 'BOOKED'
        ORDER BY b.createdAt ASC
    """)
    List<Booking> findBookedBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.memberId = :memberId
    """)
    List<Booking> findByMemberId(@Param("memberId") UUID memberId);

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
