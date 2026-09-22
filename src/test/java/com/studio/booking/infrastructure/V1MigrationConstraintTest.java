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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class V1MigrationConstraintTest {

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

    private UUID planId;

    @BeforeEach
    void insertPlan() {
        planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO membership_plan (id, name, class_credits, duration_days, price, currency, active)
                VALUES (?, 'Test Plan', 10, 30, 99.00, 'USD', true)
                """, planId);
    }

    // AC-1: migration applies to a clean database
    @Test
    void ac1_migrationAppliesToCleanDatabase() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // AC-2: ux_member_email rejects emails differing only in case
    @Test
    void ac2_duplicateEmailDifferingOnlyCaseIsRejected() {
        jdbc.update("INSERT INTO member (email, full_name, status) VALUES (?, 'Alice', 'ACTIVE')",
                "alice@example.com");
        assertThatThrownBy(() ->
                jdbc.update("INSERT INTO member (email, full_name, status) VALUES (?, 'Alice2', 'ACTIVE')",
                        "ALICE@example.com")
        ).hasMessageContaining("ux_member_email");
    }

    // AC-3 (I1): two ACTIVE memberships for one member are rejected
    @Test
    void ac3_twoActiveMembershipsForSameMemberRejected() {
        UUID memberId = insertMember("i1@example.com");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        insertMembership(memberId, planId, "ACTIVE", false, 10, start, end);
        assertThatThrownBy(() ->
                insertMembership(memberId, planId, "ACTIVE", false, 10, start, end)
        ).hasMessageContaining("ux_membership_one_active_per_member");
    }

    // AC-3 (I2): two PENDING memberships for one member are rejected
    @Test
    void ac3_twoPendingMembershipsForSameMemberRejected() {
        UUID memberId = insertMember("i2@example.com");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        insertMembership(memberId, planId, "PENDING", false, 10, start, end);
        assertThatThrownBy(() ->
                insertMembership(memberId, planId, "PENDING", false, 10, start, end)
        ).hasMessageContaining("ux_membership_one_pending_per_member");
    }

    // AC-4: unlimited=true with non-null credits_remaining is rejected
    @Test
    void ac4_unlimitedTrueWithCreditsRemainingRejected() {
        UUID memberId = insertMember("i3@example.com");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        assertThatThrownBy(() ->
                insertMembership(memberId, planId, "ACTIVE", true, 10, start, end)
        ).hasMessageContaining("ck_membership_credit_shape");
    }

    // AC-4 mirror: unlimited=false with null credits_remaining is rejected
    @Test
    void ac4_unlimitedFalseWithNullCreditsRemainingRejected() {
        UUID memberId = insertMember("i4@example.com");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO membership
                            (member_id, plan_id, status, unlimited, credits_remaining, starts_at, expires_at)
                        VALUES (?, ?, 'ACTIVE', false, NULL, ?, ?)
                        """,
                        memberId, planId,
                        Timestamp.from(start),
                        Timestamp.from(end))
        ).hasMessageContaining("ck_membership_credit_shape");
    }

    // AC-5: expires_at <= starts_at is rejected
    @Test
    void ac5_membershipExpiresAtBeforeOrEqualStartsAtRejected() {
        UUID memberId = insertMember("i5@example.com");
        Instant start = Instant.now();
        Instant end = start.minusSeconds(1);
        assertThatThrownBy(() ->
                insertMembership(memberId, planId, "ACTIVE", false, 10, start, end)
        ).hasMessageContaining("ck_membership_expires_after_starts");
    }

    // AC-6 (I9): credits_remaining cannot go negative
    @Test
    void ac6_creditsRemainingCannotGoNegative() {
        UUID memberId = insertMember("i6@example.com");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        assertThatThrownBy(() ->
                insertMembership(memberId, planId, "ACTIVE", false, -1, start, end)
        ).hasMessageContaining("ck_membership_credits_remaining_nonneg");
    }

    // AC-7: credit_transaction with delta = 0 is rejected
    @Test
    void ac7_creditTransactionWithDeltaZeroRejected() {
        UUID memberId = insertMember("i7@example.com");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(86400 * 30L);
        insertMembership(memberId, planId, "ACTIVE", false, 10, start, end);
        UUID membershipId = jdbc.queryForObject(
                "SELECT id FROM membership WHERE member_id = ?", UUID.class, memberId);
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO credit_transaction (membership_id, delta, reason, balance_after)
                        VALUES (?, 0, 'BOOKING', 10)
                        """, membershipId)
        ).hasMessageContaining("ck_credit_transaction_delta_nonzero");
    }

    // AC-8: plan with class_credits = 0 is rejected
    @Test
    void ac8_planWithClassCreditsZeroRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO membership_plan (name, class_credits, duration_days, price, currency, active)
                        VALUES ('Zero Credits Plan', 0, 30, 50.00, 'USD', true)
                        """)
        ).hasMessageContaining("ck_membership_plan_class_credits_positive");
    }

    // AC-8: plan with NULL class_credits is accepted (unlimited)
    @Test
    void ac8_planWithNullClassCreditsAccepted() {
        jdbc.update("""
                INSERT INTO membership_plan (name, class_credits, duration_days, price, currency, active)
                VALUES ('Unlimited Plan', NULL, 30, 50.00, 'USD', true)
                """);
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM membership_plan WHERE name = 'Unlimited Plan'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID insertMember(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, email, full_name, status) VALUES (?, ?, 'Test User', 'ACTIVE')",
                id, email);
        return id;
    }

    private void insertMembership(UUID memberId, UUID planId, String status,
                                  boolean unlimited, Integer credits,
                                  Instant startsAt, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO membership
                    (member_id, plan_id, status, unlimited, credits_remaining, starts_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                memberId, planId, status, unlimited, credits,
                Timestamp.from(startsAt),
                Timestamp.from(expiresAt));
    }
}
