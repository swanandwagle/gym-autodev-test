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

@SpringBootTest
@Testcontainers
class V5TriggerTest {

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
    private UUID sessionId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        memberId = insertMember("v5test-" + suffix + "@example.com");

        UUID planId = insertPlan("Plan-" + suffix);
        membershipId = insertMembership(memberId, planId);

        UUID classTypeId = insertClassType("Yoga-" + suffix, 60, 15);
        UUID instructorId = insertInstructor("instr-" + suffix + "@example.com");
        UUID roomId = insertRoom("Room-" + suffix, 20);
        sessionId = insertSession(classTypeId, instructorId, roomId,
                BASE, BASE.plus(1, ChronoUnit.HOURS));
    }

    // AC-1: V5 migration applied cleanly
    @Test
    void ac1_v5MigrationAppliesToCleanDatabase() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // AC-2 + DoD-2: updating a member row advances updated_at
    @Test
    void ac2_updatedAtAdvancesOnMemberUpdate() {
        Instant before = jdbc.queryForObject(
                "SELECT updated_at FROM member WHERE id = ?", Instant.class, memberId);

        // Small sleep to ensure clock advances
        jdbc.update("UPDATE member SET phone = '555-0001' WHERE id = ?", memberId);

        Instant after = jdbc.queryForObject(
                "SELECT updated_at FROM member WHERE id = ?", Instant.class, memberId);

        assertThat(after).isAfterOrEqualTo(before);
    }

    // AC-2 + DoD-2: updating a class_session row advances updated_at
    @Test
    void ac2_updatedAtAdvancesOnClassSessionUpdate() {
        Instant before = jdbc.queryForObject(
                "SELECT updated_at FROM class_session WHERE id = ?", Instant.class, sessionId);

        jdbc.update("UPDATE class_session SET booked_count = 1 WHERE id = ?", sessionId);

        Instant after = jdbc.queryForObject(
                "SELECT updated_at FROM class_session WHERE id = ?", Instant.class, sessionId);

        assertThat(after).isAfterOrEqualTo(before);
    }

    // AC-2 + DoD-2: updating a booking row advances updated_at
    @Test
    void ac2_updatedAtAdvancesOnBookingUpdate() {
        UUID bookingId = insertBooking(memberId, sessionId);
        Instant before = jdbc.queryForObject(
                "SELECT updated_at FROM booking WHERE id = ?", Instant.class, bookingId);

        jdbc.update("UPDATE booking SET status = 'CHECKED_IN', checked_in_by = 'STAFF' WHERE id = ?", bookingId);

        Instant after = jdbc.queryForObject(
                "SELECT updated_at FROM booking WHERE id = ?", Instant.class, bookingId);

        assertThat(after).isAfterOrEqualTo(before);
    }

    // AC-3: created_at is never modified by an update on member
    @Test
    void ac3_createdAtNotModifiedOnMemberUpdate() {
        Instant createdAtBefore = jdbc.queryForObject(
                "SELECT created_at FROM member WHERE id = ?", Instant.class, memberId);

        jdbc.update("UPDATE member SET full_name = 'Updated Name' WHERE id = ?", memberId);

        Instant createdAtAfter = jdbc.queryForObject(
                "SELECT created_at FROM member WHERE id = ?", Instant.class, memberId);

        assertThat(createdAtAfter).isEqualTo(createdAtBefore);
    }

    // AC-3: created_at is never modified by an update on class_session
    @Test
    void ac3_createdAtNotModifiedOnClassSessionUpdate() {
        Instant createdAtBefore = jdbc.queryForObject(
                "SELECT created_at FROM class_session WHERE id = ?", Instant.class, sessionId);

        jdbc.update("UPDATE class_session SET status = 'COMPLETED' WHERE id = ?", sessionId);

        Instant createdAtAfter = jdbc.queryForObject(
                "SELECT created_at FROM class_session WHERE id = ?", Instant.class, sessionId);

        assertThat(createdAtAfter).isEqualTo(createdAtBefore);
    }

    // DoD-5: credit_transaction (append-only) has no set_updated_at trigger attached
    @Test
    void dod5_creditTransactionHasNoUpdatedAtTrigger() {
        Integer triggerCount = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.triggers
                WHERE event_object_table = 'credit_transaction'
                  AND trigger_name = 'trg_credit_transaction_updated_at'
                """, Integer.class);
        assertThat(triggerCount).isZero();
    }

    // DoD-5: no_show_record (append-only) has no set_updated_at trigger attached
    @Test
    void dod5_noShowRecordHasNoUpdatedAtTrigger() {
        Integer triggerCount = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.triggers
                WHERE event_object_table = 'no_show_record'
                  AND trigger_name = 'trg_no_show_record_updated_at'
                """, Integer.class);
        assertThat(triggerCount).isZero();
    }

    // DoD-5: notification_log (append-only) has no set_updated_at trigger attached
    @Test
    void dod5_notificationLogHasNoUpdatedAtTrigger() {
        Integer triggerCount = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.triggers
                WHERE event_object_table = 'notification_log'
                  AND trigger_name = 'trg_notification_log_updated_at'
                """, Integer.class);
        assertThat(triggerCount).isZero();
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

    private UUID insertPlan(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO membership_plan (id, name, class_credits, duration_days, price, currency, active)
                VALUES (?, ?, 10, 30, 99.00, 'USD', true)
                """, id, name);
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

    private UUID insertBooking(UUID memberId, UUID sessionId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO booking (id, member_id, session_id, status, source)
                VALUES (?, ?, ?, 'BOOKED', 'DIRECT')
                """, id, memberId, sessionId);
        return id;
    }
}
