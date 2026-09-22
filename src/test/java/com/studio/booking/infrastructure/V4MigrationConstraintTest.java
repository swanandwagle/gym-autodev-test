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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class V4MigrationConstraintTest {

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

    private UUID memberId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        memberId = insertMember("v4test-" + suffix + "@example.com");
    }

    // AC-1: V4 migration applied cleanly on top of V3
    @Test
    void ac1_v4MigrationAppliesToCleanDatabase() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // AC-9: full chain V1-V5 applied (all five versions present)
    @Test
    void ac9_fullMigrationChainV1ToV5AppliesToEmptyDatabase() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true AND version IN ('1','2','3','4','5')",
                Integer.class);
        assertThat(count).isEqualTo(5);
    }

    // AC-4: second RUNNING row for the same job_name is rejected by ux_job_run_single_active
    @Test
    void ac4_secondRunningJobSameNameRejected() {
        String jobName = "membership-lifecycle-" + UUID.randomUUID();
        insertJobRun(jobName, "RUNNING");

        assertThatThrownBy(() -> insertJobRun(jobName, "RUNNING"))
                .hasMessageContaining("ux_job_run_single_active");
    }

    // AC-5: after first run transitions to SUCCEEDED, a new RUNNING row is accepted
    @Test
    void ac5_newRunningJobAcceptedAfterPreviousSucceeded() {
        String jobName = "waitlist-expiry-" + UUID.randomUUID();
        UUID firstId = insertJobRun(jobName, "RUNNING");
        jdbc.update("UPDATE job_run SET status = 'SUCCEEDED', finished_at = now() WHERE id = ?", firstId);

        UUID secondId = insertJobRun(jobName, "RUNNING");
        assertThat(secondId).isNotNull();

        Integer runningCount = jdbc.queryForObject(
                "SELECT count(*) FROM job_run WHERE job_name = ? AND status = 'RUNNING'",
                Integer.class, jobName);
        assertThat(runningCount).isEqualTo(1);
    }

    // AC-5: after first run transitions to FAILED, a new RUNNING row is accepted
    @Test
    void ac5_newRunningJobAcceptedAfterPreviousFailed() {
        String jobName = "no-show-sweep-" + UUID.randomUUID();
        UUID firstId = insertJobRun(jobName, "RUNNING");
        jdbc.update("UPDATE job_run SET status = 'FAILED', finished_at = now(), error = 'timeout' WHERE id = ?", firstId);

        UUID secondId = insertJobRun(jobName, "RUNNING");
        assertThat(secondId).isNotNull();

        Integer runningCount = jdbc.queryForObject(
                "SELECT count(*) FROM job_run WHERE job_name = ? AND status = 'RUNNING'",
                Integer.class, jobName);
        assertThat(runningCount).isEqualTo(1);
    }

    // AC-6: two different jobs can be RUNNING simultaneously
    @Test
    void ac6_twoDifferentJobsCanRunSimultaneously() {
        String job1 = "membership-lifecycle-" + UUID.randomUUID();
        String job2 = "waitlist-promotion-" + UUID.randomUUID();

        insertJobRun(job1, "RUNNING");
        insertJobRun(job2, "RUNNING");

        Integer runningCount = jdbc.queryForObject(
                "SELECT count(*) FROM job_run WHERE job_name IN (?, ?) AND status = 'RUNNING'",
                Integer.class, job1, job2);
        assertThat(runningCount).isEqualTo(2);
    }

    // AC-7: notification_log.payload accepts and returns arbitrary JSON structure intact
    @Test
    void ac7_notificationLogPayloadAcceptsArbitraryJson() {
        String complexJson = "{\"sessionId\":\"abc-123\",\"nested\":{\"a\":1,\"b\":[true,null]}}";
        UUID logId = insertNotificationLog(memberId, "BOOKING_CONFIRMED", "EMAIL", complexJson, "SYSTEM");

        String retrieved = jdbc.queryForObject(
                "SELECT payload::text FROM notification_log WHERE id = ?",
                String.class, logId);
        assertThat(retrieved).isNotNull();
        // jsonb normalises whitespace but preserves all keys/values
        assertThat(retrieved).contains("sessionId");
        assertThat(retrieved).contains("abc-123");
        assertThat(retrieved).contains("nested");
    }

    // AC-8: invalid channel value is rejected
    @Test
    void ac8_invalidNotificationChannelRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO notification_log (member_id, event_type, channel, payload, triggered_by)
                        VALUES (?, 'BOOKING_CONFIRMED', 'PUSH', '{}', 'SYSTEM')
                        """, memberId)
        ).hasMessageContaining("ck_notification_log_channel");
    }

    // AC-8: invalid triggered_by value is rejected
    @Test
    void ac8_invalidTriggeredByRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO notification_log (member_id, event_type, channel, payload, triggered_by)
                        VALUES (?, 'BOOKING_CONFIRMED', 'EMAIL', '{}', 'MEMBER')
                        """, memberId)
        ).hasMessageContaining("ck_notification_log_triggered_by");
    }

    // AC-8: invalid job_run status is rejected
    @Test
    void ac8_invalidJobRunStatusRejected() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO job_run (job_name, status)
                        VALUES ('test-job', 'PENDING')
                        """)
        ).hasMessageContaining("ck_job_run_status");
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

    private UUID insertJobRun(String jobName, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO job_run (id, job_name, status)
                VALUES (?, ?, ?)
                """, id, jobName, status);
        return id;
    }

    private UUID insertNotificationLog(UUID memberId, String eventType, String channel,
                                        String payload, String triggeredBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_log (id, member_id, event_type, channel, payload, triggered_by)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """, id, memberId, eventType, channel, payload, triggeredBy);
        return id;
    }
}
