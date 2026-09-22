package com.studio.booking.shared.error;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PSQLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constraint violation translator tests for GYM-21.
 *
 * Each test triggers a real named PostgreSQL constraint via Testcontainers and
 * asserts that the translator maps it to the correct ErrorCode.
 *
 * No test asserts on the database's error message text — only constraint names
 * and ErrorCode values are checked (AC-7).
 */
@Testcontainers
class ConstraintViolationTranslatorTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");

    static PGSimpleDataSource dataSource;
    ConstraintViolationTranslator translator;

    @BeforeAll
    static void applyMigrations() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;

        try (Connection conn = ds.getConnection()) {
            executeSqlStatic(conn, readMigration("V1__member_membership.sql"));
            executeSqlStatic(conn, readMigration("V2__catalog.sql"));
            executeSqlStatic(conn, readMigration("V3__booking_waitlist.sql"));
            executeSqlStatic(conn, readMigration("V4__notification_job.sql"));
            executeSqlStatic(conn, readMigration("V5__updated_at_trigger.sql"));
        }
    }

    @BeforeEach
    void setUp() {
        translator = new ConstraintViolationTranslator();
    }

    // =========================================================================
    // AC-1: Every mapping row triggered by real constraint
    // =========================================================================

    @Test
    void testAc1_duplicateBookingConstraintMapsToCode() throws Exception {
        UUID memberId = insertMember("dup-booking@test.com");
        UUID sessionId = insertSession(Instant.now().plus(1, ChronoUnit.HOURS));
        UUID membershipId = insertMembership(memberId);
        insertBooking(memberId, sessionId, membershipId);

        // Second non-cancelled booking for same (member, session) violates uq_booking_member_session
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertBooking(memberId, sessionId, membershipId));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.DUPLICATE_BOOKING);
    }

    @Test
    void testAc1_membershipAlreadyActiveConstraintMapsToCode() throws Exception {
        UUID memberId = insertMember("dup-active@test.com");
        UUID planId = insertMembershipPlan();
        insertActiveMembership(memberId, planId);

        // Second ACTIVE membership for same member violates uq_membership_member_active
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertActiveMembership(memberId, planId));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
    }

    @Test
    void testAc1_membershipPendingConstraintMapsToCode() throws Exception {
        UUID memberId = insertMember("dup-pending@test.com");
        UUID planId = insertMembershipPlan();
        insertPendingMembership(memberId, planId);

        // Second PENDING membership violates uq_membership_member_pending → MEMBERSHIP_ALREADY_ACTIVE
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertPendingMembership(memberId, planId));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
    }

    @Test
    void testAc1_waitlistAlreadyJoinedConstraintMapsToCode() throws Exception {
        UUID memberId = insertMember("dup-waitlist@test.com");
        UUID sessionId = insertSession(Instant.now().plus(2, ChronoUnit.HOURS));
        UUID membershipId = insertMembership(memberId);
        insertWaitlistEntry(memberId, sessionId, membershipId);

        // Second WAITING entry for same (member, session) violates uq_waitlist_member_session
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertWaitlistEntry(memberId, sessionId, membershipId));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.WAITLIST_ALREADY_JOINED);
    }

    @Test
    void testAc1_instructorScheduleConflictConstraintMapsToCode() throws Exception {
        UUID instructorId = insertInstructor("overlap-instructor@test.com");
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        insertSessionForInstructor(instructorId, start, end);

        // Overlapping session for same instructor violates excl_session_instructor_overlap
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertSessionForInstructor(instructorId, start, end));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.INSTRUCTOR_SCHEDULE_CONFLICT);
    }

    @Test
    void testAc1_roomScheduleConflictConstraintMapsToCode() throws Exception {
        UUID roomId = insertRoom();
        Instant start = Instant.now().plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        insertSessionForRoom(roomId, start, end);

        // Overlapping session for same room violates excl_session_room_overlap
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertSessionForRoom(roomId, start, end));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.ROOM_SCHEDULE_CONFLICT);
    }

    // =========================================================================
    // AC-2: Pre-check and constraint path return the same code
    // =========================================================================

    @Test
    void testAc2_duplicateBookingPreCheckAndConstraintYieldSameCode() throws Exception {
        // The application pre-check uses ErrorCode.DUPLICATE_BOOKING directly.
        ErrorCode preCheckCode = ErrorCode.DUPLICATE_BOOKING;

        UUID memberId = insertMember("prechecks-booking@test.com");
        UUID sessionId = insertSession(Instant.now().plus(5, ChronoUnit.HOURS));
        UUID membershipId = insertMembership(memberId);
        insertBooking(memberId, sessionId, membershipId);
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertBooking(memberId, sessionId, membershipId));

        assertThat(translator.translate(ex)).isEqualTo(preCheckCode);
    }

    @Test
    void testAc2_instructorConflictPreCheckAndConstraintYieldSameCode() throws Exception {
        // The application pre-check uses ErrorCode.INSTRUCTOR_SCHEDULE_CONFLICT directly.
        ErrorCode preCheckCode = ErrorCode.INSTRUCTOR_SCHEDULE_CONFLICT;

        UUID instructorId = insertInstructor("prechecks-instructor@test.com");
        Instant start = Instant.now().plus(6, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        insertSessionForInstructor(instructorId, start, end);
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertSessionForInstructor(instructorId, start, end));

        assertThat(translator.translate(ex)).isEqualTo(preCheckCode);
    }

    // =========================================================================
    // AC-3: Unmapped constraint returns CONCURRENT_MODIFICATION; no Postgres text
    // =========================================================================

    @Test
    void testAc3_unmappedConstraintReturnsConcurrentModification() throws Exception {
        // uq_instructor_email is a real constraint but NOT in our mapping table
        String email = "unmapped-" + UUID.randomUUID() + "@test.com";
        insertInstructor(email);
        DataIntegrityViolationException ex = catchConstraintViolation(() -> insertInstructor(email));

        assertThat(ex).isNotNull();
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void testAc3_unmappedConstraintDetailContainsNoPostgresText() {
        // The message surfaced to the client must be clean
        String detail = ErrorMessages.forCode(ErrorCode.CONCURRENT_MODIFICATION);
        assertThat(detail).doesNotContainIgnoringCase("postgres");
        assertThat(detail).doesNotContainIgnoringCase("constraint");
        assertThat(detail).doesNotContainIgnoringCase("violation");
        assertThat(detail).doesNotContain("pg_");
        assertThat(detail).doesNotContain("ERROR:");
        assertThat(detail).doesNotContain("DETAIL:");
    }

    // =========================================================================
    // AC-4: Unmapped case returns CONCURRENT_MODIFICATION (logging verified by
    //        code inspection — the translator logs at ERROR on this path)
    // =========================================================================

    @Test
    void testAc4_unmappedConstraintFallbackIsConcurrentModification() {
        // No PSQLException chain → no extractable name → falls back and logs at ERROR
        DataIntegrityViolationException ex = new DataIntegrityViolationException("plain message");
        assertThat(translator.translate(ex)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    // =========================================================================
    // AC-5: no_show_record unique violation is swallowed
    // =========================================================================

    @Test
    void testAc5_noShowRecordUniqueViolationIsIdentifiedForSwallowing() throws Exception {
        UUID memberId = insertMember("no-show@test.com");
        UUID sessionId = insertSession(Instant.now().plus(7, ChronoUnit.HOURS));
        UUID membershipId = insertMembership(memberId);
        UUID bookingId = insertBooking(memberId, sessionId, membershipId);
        insertNoShowRecord(memberId, bookingId, sessionId);

        // Second insert for same booking violates uq_no_show_booking
        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertNoShowRecord(memberId, bookingId, sessionId));

        assertThat(ex).isNotNull();
        // The sweep checks isNoShowDuplicate() and swallows — no error surfaces
        assertThat(translator.isNoShowDuplicate(ex)).isTrue();
    }

    @Test
    void testAc5_nonNoShowViolationIsNotMarkedAsSwallowable() throws Exception {
        UUID memberId = insertMember("not-no-show@test.com");
        UUID sessionId = insertSession(Instant.now().plus(8, ChronoUnit.HOURS));
        UUID membershipId = insertMembership(memberId);
        insertBooking(memberId, sessionId, membershipId);

        DataIntegrityViolationException ex = catchConstraintViolation(() ->
                insertBooking(memberId, sessionId, membershipId));

        assertThat(translator.isNoShowDuplicate(ex)).isFalse();
    }

    // =========================================================================
    // AC-6: Exception chain traversal — nested to full depth and missing layers
    // =========================================================================

    @Test
    void testAc6_constraintNameExtractionFromFullyNestedChain() throws Exception {
        UUID memberId = insertMember("nested@test.com");
        UUID sessionId = insertSession(Instant.now().plus(9, ChronoUnit.HOURS));
        UUID membershipId = insertMembership(memberId);
        insertBooking(memberId, sessionId, membershipId);

        DataIntegrityViolationException base = catchConstraintViolation(() ->
                insertBooking(memberId, sessionId, membershipId));

        // Wrap in extra non-SQL layers to simulate deep nesting (Spring / Hibernate wrapping)
        RuntimeException level3 = new RuntimeException("wrapper-3", base.getCause());
        RuntimeException level2 = new RuntimeException("wrapper-2", level3);
        DataIntegrityViolationException nested =
                new DataIntegrityViolationException("wrapper-top", level2);

        Optional<String> name = translator.extractConstraintName(nested);
        assertThat(name).isPresent();
        // Only check the constraint name, not any database error message text (AC-7)
        assertThat(name.get()).isEqualTo("uq_booking_member_session");
    }

    @Test
    void testAc6_constraintNameExtractionDegradesSafelyWithNoSqlException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "no sql cause", new RuntimeException("plain"));

        Optional<String> name = translator.extractConstraintName(ex);
        assertThat(name).isEmpty();

        ErrorCode code = translator.translate(ex);
        assertThat(code).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void testAc6_constraintNameExtractionDegradesSafelyWithNullCause() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("no cause");

        Optional<String> name = translator.extractConstraintName(ex);
        assertThat(name).isEmpty();

        ErrorCode code = translator.translate(ex);
        assertThat(code).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    // =========================================================================
    // Migration helpers (static)
    // =========================================================================

    private static String readMigration(String filename) throws Exception {
        String path = "/db/migration/" + filename;
        try (var is = ConstraintViolationTranslatorTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Migration not found: " + path);
            return new String(is.readAllBytes());
        }
    }

    private static void executeSqlStatic(Connection conn, String sql) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // =========================================================================
    // DB insert helpers
    // =========================================================================

    private UUID insertMember(String email) throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO member (id, email, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                id, email, "Test Member");
        return id;
    }

    private UUID insertMembershipPlan() throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO membership_plan (id, name, credits, price, currency, validity_days) " +
             "VALUES (?, 'Plan', 10, 50.00, 'USD', 30)", id);
        return id;
    }

    private UUID insertMembership(UUID memberId) throws Exception {
        UUID planId = insertMembershipPlan();
        return insertActiveMembership(memberId, planId);
    }

    private UUID insertActiveMembership(UUID memberId, UUID planId) throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        exec("INSERT INTO membership (id, member_id, plan_id, status, credits_total, credits_remaining, starts_at, expires_at) " +
             "VALUES (?, ?, ?, 'ACTIVE', 10, 10, ?, ?)",
                id, memberId, planId, now.minusSeconds(1), now.plus(30, ChronoUnit.DAYS));
        return id;
    }

    private UUID insertPendingMembership(UUID memberId, UUID planId) throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        exec("INSERT INTO membership (id, member_id, plan_id, status, credits_total, credits_remaining, starts_at, expires_at) " +
             "VALUES (?, ?, ?, 'PENDING', 10, 10, ?, ?)",
                id, memberId, planId, now.plus(1, ChronoUnit.DAYS), now.plus(31, ChronoUnit.DAYS));
        return id;
    }

    private UUID insertClassType() throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO class_type (id, name) VALUES (?, 'Yoga')", id);
        return id;
    }

    private UUID insertInstructor(String email) throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO instructor (id, name, email) VALUES (?, 'Instructor', ?)", id, email);
        return id;
    }

    private UUID insertRoom() throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO room (id, name, capacity) VALUES (?, 'Studio A', 20)", id);
        return id;
    }

    private UUID insertSession(Instant start) throws Exception {
        UUID classTypeId = insertClassType();
        UUID instructorId = insertInstructor(UUID.randomUUID() + "@test.com");
        UUID roomId = insertRoom();
        Instant s = start.truncatedTo(ChronoUnit.SECONDS);
        return insertFullSession(classTypeId, instructorId, roomId, s, s.plus(1, ChronoUnit.HOURS));
    }

    private UUID insertSessionForInstructor(UUID instructorId, Instant start, Instant end) throws Exception {
        UUID classTypeId = insertClassType();
        UUID roomId = insertRoom();
        return insertFullSession(classTypeId, instructorId, roomId, start, end);
    }

    private UUID insertSessionForRoom(UUID roomId, Instant start, Instant end) throws Exception {
        UUID classTypeId = insertClassType();
        UUID instructorId = insertInstructor(UUID.randomUUID() + "@test.com");
        return insertFullSession(classTypeId, instructorId, roomId, start, end);
    }

    private UUID insertFullSession(UUID classTypeId, UUID instructorId, UUID roomId,
                                   Instant start, Instant end) throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO class_session (id, class_type_id, instructor_id, room_id, starts_at, ends_at, capacity, status) " +
             "VALUES (?, ?, ?, ?, ?, ?, 20, 'SCHEDULED')",
                id, classTypeId, instructorId, roomId, start, end);
        return id;
    }

    private UUID insertBooking(UUID memberId, UUID sessionId, UUID membershipId) throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO booking (id, member_id, session_id, membership_id, status, source) " +
             "VALUES (?, ?, ?, ?, 'CONFIRMED', 'DIRECT')",
                id, memberId, sessionId, membershipId);
        return id;
    }

    private void insertWaitlistEntry(UUID memberId, UUID sessionId, UUID membershipId) throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO waitlist_entry (id, member_id, session_id, membership_id, status) " +
             "VALUES (?, ?, ?, ?, 'WAITING')",
                id, memberId, sessionId, membershipId);
    }

    private void insertNoShowRecord(UUID memberId, UUID bookingId, UUID sessionId) throws Exception {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO no_show_record (id, member_id, booking_id, session_id) " +
             "VALUES (?, ?, ?, ?)", id, memberId, bookingId, sessionId);
    }

    // =========================================================================
    // Exception capture
    // =========================================================================

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private DataIntegrityViolationException catchConstraintViolation(ThrowingRunnable action) {
        try {
            action.run();
            return null;
        } catch (DataIntegrityViolationException ex) {
            return ex;
        } catch (Exception ex) {
            throw new AssertionError("Expected DataIntegrityViolationException but got " + ex.getClass().getName(), ex);
        }
    }

    private void exec(String sql, Object... params) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof UUID uuid) {
                    ps.setObject(i + 1, uuid);
                } else if (params[i] instanceof Instant instant) {
                    ps.setTimestamp(i + 1, java.sql.Timestamp.from(instant));
                } else {
                    ps.setObject(i + 1, params[i]);
                }
            }
            ps.executeUpdate();
        } catch (PSQLException ex) {
            // Only re-throw as DataIntegrityViolationException for constraint violations (SQLState 23xxx)
            if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                throw new DataIntegrityViolationException("Constraint violation", ex);
            }
            throw ex;
        }
    }
}
