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
class V2MigrationConstraintTest {

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

    private UUID instructorId;
    private UUID roomId;
    private UUID classTypeId;

    // Base time anchor for all session tests
    private static final Instant BASE = Instant.parse("2026-10-01T09:00:00Z");

    @BeforeEach
    void setUp() {
        instructorId = insertInstructor("test-instructor@example.com", "Test Instructor");
        roomId = insertRoom("Studio A", 20);
        classTypeId = insertClassType("Yoga", 60, 15);
    }

    // AC-1: V2 migration applied cleanly on top of V1
    @Test
    void ac1_v2MigrationAppliesToCleanDatabase() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // AC-2: two SCHEDULED sessions for same instructor with overlapping intervals are rejected
    @Test
    void ac2_overlappingSessionsSameInstructorRejected() {
        // 09:00-10:00
        insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 10, "SCHEDULED");

        // 09:30-10:30 — overlaps with the first
        UUID room2 = insertRoom("Studio B", 20);
        assertThatThrownBy(() ->
                insertSession(instructorId, room2, classTypeId,
                        BASE.plus(30, ChronoUnit.MINUTES),
                        BASE.plus(90, ChronoUnit.MINUTES),
                        10, "SCHEDULED")
        ).hasMessageContaining("ex_session_instructor");
    }

    // AC-3: two SCHEDULED sessions for same room with overlapping intervals are rejected
    @Test
    void ac3_overlappingSessionsSameRoomRejected() {
        // 09:00-10:00
        insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 10, "SCHEDULED");

        // 09:30-10:30 — overlaps with the first
        UUID instructor2 = insertInstructor("instructor2@example.com", "Instructor Two");
        assertThatThrownBy(() ->
                insertSession(instructor2, roomId, classTypeId,
                        BASE.plus(30, ChronoUnit.MINUTES),
                        BASE.plus(90, ChronoUnit.MINUTES),
                        10, "SCHEDULED")
        ).hasMessageContaining("ex_session_room");
    }

    // AC-4: back-to-back sessions (first ends exactly when second starts) for the same instructor
    //        AND same room are both accepted — proves half-open [start, end) semantics
    @Test
    void ac4_backToBackSessionsSameInstructorAccepted() {
        // 09:00-10:00
        insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 10, "SCHEDULED");

        // 10:00-11:00 — starts exactly when first ends; must NOT be rejected
        UUID room2 = insertRoom("Studio C", 20);
        insertSession(instructorId, room2, classTypeId,
                BASE.plus(1, ChronoUnit.HOURS),
                BASE.plus(2, ChronoUnit.HOURS),
                10, "SCHEDULED");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM class_session WHERE instructor_id = ?",
                Integer.class, instructorId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void ac4_backToBackSessionsSameRoomAccepted() {
        // 09:00-10:00
        insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 10, "SCHEDULED");

        // 10:00-11:00 — starts exactly when first ends; must NOT be rejected
        UUID instructor2 = insertInstructor("instructor3@example.com", "Instructor Three");
        insertSession(instructor2, roomId, classTypeId,
                BASE.plus(1, ChronoUnit.HOURS),
                BASE.plus(2, ChronoUnit.HOURS),
                10, "SCHEDULED");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM class_session WHERE room_id = ?",
                Integer.class, roomId);
        assertThat(count).isEqualTo(2);
    }

    // AC-5: cancelling a session then inserting a new session in the same slot succeeds
    @Test
    void ac5_cancelledSlotCanBeReused() {
        UUID sessionId = insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 10, "SCHEDULED");

        // Cancel the first session
        jdbc.update("UPDATE class_session SET status = 'CANCELLED' WHERE id = ?", sessionId);

        // Insert a new session in the exact same slot for the same instructor and room
        insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 10, "SCHEDULED");

        Integer scheduledCount = jdbc.queryForObject(
                "SELECT count(*) FROM class_session WHERE instructor_id = ? AND status = 'SCHEDULED'",
                Integer.class, instructorId);
        assertThat(scheduledCount).isEqualTo(1);
    }

    // AC-6: a session with ends_at <= starts_at is rejected
    @Test
    void ac6_sessionWithEndsAtEqualToStartsAtRejected() {
        assertThatThrownBy(() ->
                insertSession(instructorId, roomId, classTypeId,
                        BASE, BASE, 10, "SCHEDULED")
        ).hasMessageContaining("ck_session_time_order");
    }

    @Test
    void ac6_sessionWithEndsAtBeforeStartsAtRejected() {
        assertThatThrownBy(() ->
                insertSession(instructorId, roomId, classTypeId,
                        BASE, BASE.minus(1, ChronoUnit.SECONDS), 10, "SCHEDULED")
        ).hasMessageContaining("ck_session_time_order");
    }

    // AC-7: booked_count cannot exceed capacity (I5)
    @Test
    void ac7_bookedCountCannotExceedCapacity() {
        UUID sessionId = insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 5, "SCHEDULED");

        assertThatThrownBy(() ->
                jdbc.update("UPDATE class_session SET booked_count = 6 WHERE id = ?", sessionId)
        ).hasMessageContaining("ck_session_booked_not_exceed_capacity");
    }

    // AC-7: booked_count cannot go negative
    @Test
    void ac7_bookedCountCannotGoNegative() {
        UUID sessionId = insertSession(instructorId, roomId, classTypeId,
                BASE, BASE.plus(1, ChronoUnit.HOURS), 5, "SCHEDULED");

        assertThatThrownBy(() ->
                jdbc.update("UPDATE class_session SET booked_count = -1 WHERE id = ?", sessionId)
        ).hasMessageContaining("ck_session_booked_count_nonneg");
    }

    // AC-8: duration_minutes outside 5-480 is rejected
    @Test
    void ac8_classTypeDurationMinutesBelowMinRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO class_type (name, duration_minutes, default_capacity, active)
                        VALUES ('Too Short', 4, 10, true)
                        """)
        ).hasMessageContaining("ck_class_type_duration_range");
    }

    @Test
    void ac8_classTypeDurationMinutesAboveMaxRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO class_type (name, duration_minutes, default_capacity, active)
                        VALUES ('Too Long', 481, 10, true)
                        """)
        ).hasMessageContaining("ck_class_type_duration_range");
    }

    // AC-8: default_capacity outside 1-500 is rejected
    @Test
    void ac8_classTypeDefaultCapacityZeroRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO class_type (name, duration_minutes, default_capacity, active)
                        VALUES ('Zero Cap', 60, 0, true)
                        """)
        ).hasMessageContaining("ck_class_type_default_capacity_range");
    }

    @Test
    void ac8_classTypeDefaultCapacityAboveMaxRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO class_type (name, duration_minutes, default_capacity, active)
                        VALUES ('Too Many', 60, 501, true)
                        """)
        ).hasMessageContaining("ck_class_type_default_capacity_range");
    }

    // AC-9: duplicate instructor emails differing only in case are rejected
    @Test
    void ac9_duplicateInstructorEmailDifferingOnlyCaseRejected() {
        assertThatThrownBy(() ->
                insertInstructor("TEST-INSTRUCTOR@EXAMPLE.COM", "Duplicate Instructor")
        ).hasMessageContaining("ux_instructor_email");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID insertInstructor(String email, String fullName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO instructor (id, email, full_name, active)
                VALUES (?, ?, ?, true)
                """, id, email, fullName);
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

    private UUID insertSession(UUID instructorId, UUID roomId, UUID classTypeId,
                               Instant startsAt, Instant endsAt,
                               int capacity, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO class_session
                    (id, class_type_id, instructor_id, room_id, starts_at, ends_at, capacity, booked_count, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                """,
                id, classTypeId, instructorId, roomId,
                Timestamp.from(startsAt), Timestamp.from(endsAt),
                capacity, status);
        return id;
    }
}
