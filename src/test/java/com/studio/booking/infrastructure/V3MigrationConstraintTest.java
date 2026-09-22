package com.studio.booking.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class V3MigrationConstraintTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    JdbcTemplate jdbc;

    private static final Instant BASE = Instant.parse("2026-10-01T09:00:00Z");

    private UUID memberId;
    private UUID member2Id;
    private UUID sessionId;
    private UUID planId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        memberId = insertMember("member1-" + suffix + "@example.com");
        member2Id = insertMember("member2-" + suffix + "@example.com");
        planId = insertPlan();
        membershipId = insertMembership(memberId, planId);

        UUID classTypeId = insertClassType("Yoga-" + suffix, 60, 15);
        UUID instructorId = insertInstructor("instructor-" + suffix + "@example.com");
        UUID roomId = insertRoom("Studio-" + suffix, 20);
        sessionId = insertSession(classTypeId, instructorId, roomId,
                BASE, BASE.plus(1, ChronoUnit.HOURS));
    }

    // AC-1: V3 migration applied cleanly on top of V2
    @Test
    void ac1_v3MigrationAppliesToCleanDatabase() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // AC-2: two BOOKED bookings for the same member and session are rejected (I3)
    @Test
    void ac2_twoBookedBookingsSameMemberSessionRejected() {
        insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, null);

        assertThatThrownBy(() ->
                insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, null)
        ).hasMessageContaining("ux_booking_one_live_per_member_session");
    }

    // AC-3: a CANCELLED booking does not block a new BOOKED booking for the same member+session
    @Test
    void ac3_cancelledBookingDoesNotBlockRebook() {
        UUID firstBookingId = insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, null);

        // Cancel the first booking
        jdbc.update("UPDATE booking SET status = 'CANCELLED', cancellation_type = 'NORMAL' WHERE id = ?",
                firstBookingId);

        // Should be able to book again
        UUID secondBookingId = insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, null);

        Integer liveCount = jdbc.queryForObject(
                "SELECT count(*) FROM booking WHERE member_id = ? AND session_id = ? AND status <> 'CANCELLED'",
                Integer.class, memberId, sessionId);
        assertThat(liveCount).isEqualTo(1);
        assertThat(secondBookingId).isNotNull();
    }

    // AC-4: two WAITING entries for the same member and session are rejected (I4)
    @Test
    void ac4_twoWaitingEntriesSameMemberSessionRejected() {
        insertWaitlistEntry(sessionId, memberId, 1, "WAITING");

        assertThatThrownBy(() ->
                insertWaitlistEntry(sessionId, memberId, 2, "WAITING")
        ).hasMessageContaining("ux_waitlist_one_waiting_per_member_session");
    }

    // AC-4 supplement: a PROMOTED entry does not block a new WAITING entry for the same member+session
    @Test
    void ac4_promotedEntryDoesNotBlockNewWaitingEntry() {
        UUID entryId = insertWaitlistEntry(sessionId, memberId, 1, "WAITING");
        jdbc.update("UPDATE waitlist_entry SET status = 'PROMOTED' WHERE id = ?", entryId);

        // Should succeed now that the previous entry is PROMOTED (not WAITING)
        insertWaitlistEntry(sessionId, memberId, 2, "WAITING");

        Integer waitingCount = jdbc.queryForObject(
                "SELECT count(*) FROM waitlist_entry WHERE member_id = ? AND session_id = ? AND status = 'WAITING'",
                Integer.class, memberId, sessionId);
        assertThat(waitingCount).isEqualTo(1);
    }

    // AC-5: two waitlist entries with the same session_id and sequence_no are rejected
    @Test
    void ac5_duplicateSequenceNoSameSessionRejected() {
        insertWaitlistEntry(sessionId, memberId, 1, "WAITING");

        assertThatThrownBy(() ->
                insertWaitlistEntry(sessionId, member2Id, 1, "WAITING")
        ).hasMessageContaining("ux_waitlist_sequence_no_per_session");
    }

    // AC-6: two no-show records for the same booking are rejected (sweep idempotency)
    @Test
    void ac6_duplicateNoShowSameBookingRejected() {
        UUID bookingId = insertBooking(memberId, sessionId, "NO_SHOW", "DIRECT", null, null);
        insertNoShowRecord(bookingId, memberId, sessionId);

        assertThatThrownBy(() ->
                insertNoShowRecord(bookingId, memberId, sessionId)
        ).hasMessageContaining("ux_no_show_record_per_booking");
    }

    // AC-7: same idempotency_key for the same member is rejected
    @Test
    void ac7_duplicateIdempotencyKeySameMemberRejected() {
        insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, "idem-key-abc");

        UUID session2 = insertSession(
                jdbc.queryForObject("SELECT id FROM class_type LIMIT 1", UUID.class),
                jdbc.queryForObject("SELECT id FROM instructor LIMIT 1", UUID.class),
                insertRoom("Studio B", 20),
                BASE.plus(2, ChronoUnit.HOURS), BASE.plus(3, ChronoUnit.HOURS));

        assertThatThrownBy(() ->
                insertBooking(memberId, session2, "BOOKED", "DIRECT", null, "idem-key-abc")
        ).hasMessageContaining("ux_booking_idempotency_key_per_member");
    }

    // AC-7: same idempotency_key for a different member is accepted
    @Test
    void ac7_sameIdempotencyKeyDifferentMemberAccepted() {
        insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, "idem-key-xyz");

        UUID session2 = insertSession(
                jdbc.queryForObject("SELECT id FROM class_type LIMIT 1", UUID.class),
                jdbc.queryForObject("SELECT id FROM instructor LIMIT 1", UUID.class),
                insertRoom("Studio C", 20),
                BASE.plus(2, ChronoUnit.HOURS), BASE.plus(3, ChronoUnit.HOURS));

        // Different member, same key — must succeed
        insertBooking(member2Id, session2, "BOOKED", "DIRECT", null, "idem-key-xyz");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM booking WHERE idempotency_key = 'idem-key-xyz'",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    // AC-8: invalid booking status is rejected
    @Test
    void ac8_invalidBookingStatusRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO booking (member_id, session_id, status, source)
                        VALUES (?, ?, 'INVALID_STATUS', 'DIRECT')
                        """, memberId, sessionId)
        ).hasMessageContaining("ck_booking_status");
    }

    // AC-8: invalid booking source is rejected
    @Test
    void ac8_invalidBookingSourceRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO booking (member_id, session_id, status, source)
                        VALUES (?, ?, 'BOOKED', 'PHONE')
                        """, memberId, sessionId)
        ).hasMessageContaining("ck_booking_source");
    }

    // AC-8: invalid cancellation_type is rejected
    @Test
    void ac8_invalidCancellationTypeRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO booking (member_id, session_id, status, source, cancellation_type)
                        VALUES (?, ?, 'CANCELLED', 'DIRECT', 'IMMEDIATE')
                        """, memberId, sessionId)
        ).hasMessageContaining("ck_booking_cancellation_type");
    }

    // AC-8: invalid checked_in_by is rejected
    @Test
    void ac8_invalidCheckedInByRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO booking (member_id, session_id, status, source, checked_in_by)
                        VALUES (?, ?, 'CHECKED_IN', 'DIRECT', 'ADMIN')
                        """, memberId, sessionId)
        ).hasMessageContaining("ck_booking_checked_in_by");
    }

    // AC-8: invalid skip_reason is rejected
    @Test
    void ac8_invalidSkipReasonRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO waitlist_entry (session_id, member_id, status, sequence_no, skip_reason)
                        VALUES (?, ?, 'SKIPPED', 1, 'UNKNOWN_REASON')
                        """, sessionId, memberId)
        ).hasMessageContaining("ck_waitlist_entry_skip_reason");
    }

    // AC-9: credit_transaction.booking_id enforces referential integrity
    @Test
    void ac9_creditTransactionBookingIdEnforcesReferentialIntegrity() {
        UUID nonExistentBookingId = UUID.randomUUID();

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO credit_transaction (membership_id, delta, reason, balance_after, booking_id)
                        VALUES (?, -1, 'BOOKING', 9, ?)
                        """, membershipId, nonExistentBookingId)
        ).hasMessageContaining("credit_transaction_booking_id_fkey");
    }

    // AC-9: credit_transaction.booking_id with a valid booking id is accepted
    @Test
    void ac9_creditTransactionBookingIdAcceptsValidReference() {
        UUID bookingId = insertBooking(memberId, sessionId, "BOOKED", "DIRECT", null, null);

        jdbc.update("""
                INSERT INTO credit_transaction (membership_id, delta, reason, balance_after, booking_id)
                VALUES (?, -1, 'BOOKING', 9, ?)
                """, membershipId, bookingId);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM credit_transaction WHERE booking_id = ?",
                Integer.class, bookingId);
        assertThat(count).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID insertMember(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO member (id, email, full_name, status)
                VALUES (?, ?, 'Test User', 'ACTIVE')
                """, id, email);
        return id;
    }

    private UUID insertPlan() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO membership_plan (id, name, class_credits, duration_days, price, currency, active)
                VALUES (?, 'Test Plan', 10, 30, 99.00, 'USD', true)
                """, id);
        return id;
    }

    private UUID insertMembership(UUID memberId, UUID planId) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        jdbc.update("""
                INSERT INTO membership (id, member_id, plan_id, status, unlimited, credits_remaining, starts_at, expires_at)
                VALUES (?, ?, ?, 'ACTIVE', false, 10, ?, ?)
                """, id, memberId, planId, Timestamp.from(start), Timestamp.from(end));
        return id;
    }

    private UUID insertInstructor(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO instructor (id, email, full_name, active)
                VALUES (?, ?, 'Test Instructor', true)
                """, id, email);
        return id;
    }

    private UUID insertRoom(String name, int capacity) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO room (id, name, capacity, active)
                VALUES (?, ?, ?, true)
                """, id, name, capacity);
        return id;
    }

    private UUID insertClassType(String name, int durationMinutes, int defaultCapacity) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO class_type (id, name, duration_minutes, default_capacity, active)
                VALUES (?, ?, ?, ?, true)
                """, id, name, durationMinutes, defaultCapacity);
        return id;
    }

    private UUID insertSession(UUID classTypeId, UUID instructorId, UUID roomId,
                               Instant startsAt, Instant endsAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO class_session
                    (id, class_type_id, instructor_id, room_id, starts_at, ends_at, capacity, booked_count, status)
                VALUES (?, ?, ?, ?, ?, ?, 20, 0, 'SCHEDULED')
                """,
                id, classTypeId, instructorId, roomId,
                Timestamp.from(startsAt), Timestamp.from(endsAt));
        return id;
    }

    private UUID insertBooking(UUID memberId, UUID sessionId, String status, String source,
                               String cancellationType, String idempotencyKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO booking (id, member_id, session_id, status, source, cancellation_type, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, memberId, sessionId, status, source, cancellationType, idempotencyKey);
        return id;
    }

    private UUID insertWaitlistEntry(UUID sessionId, UUID memberId, int sequenceNo, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO waitlist_entry (id, session_id, member_id, status, sequence_no)
                VALUES (?, ?, ?, ?, ?)
                """, id, sessionId, memberId, status, sequenceNo);
        return id;
    }

    private UUID insertNoShowRecord(UUID bookingId, UUID memberId, UUID sessionId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO no_show_record (id, booking_id, member_id, session_id)
                VALUES (?, ?, ?, ?)
                """, id, bookingId, memberId, sessionId);
        return id;
    }
}
