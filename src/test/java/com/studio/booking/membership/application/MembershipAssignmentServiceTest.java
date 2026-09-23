package com.studio.booking.membership.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.api.AssignMembershipRequest;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.time.StudioTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MembershipAssignmentServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("studio.timezone", () -> "America/New_York");
    }

    @Autowired private MembershipService membershipService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MembershipPlanRepository planRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private Clock clock;
    @Autowired private StudioTimeZone studioTimeZone;

    private UUID memberId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        planRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = new Member("test@example.com", "Test User", "555-1234", "ACTIVE", clock);
        Member saved = memberRepository.save(member);
        memberId = saved.getId();

        MembershipPlan plan = new MembershipPlan(
            "Test Plan",
            10,
            30,
            new BigDecimal("99.99"),
            "USD",
            "BASIC"
        );
        MembershipPlan savedPlan = planRepository.save(plan);
        planId = savedPlan.getId();
    }

    @Test
    void test_ac13_expiry_computed_correctly_dst_boundary() throws Exception {
        // This test verifies expiry computation across DST boundary in America/New_York timezone
        // Test setup uses studio.timezone=America/New_York
        // Start: March 7, 2025, 10:00 AM Eastern (15:00 UTC)
        // +30 days: April 6, 2025, 10:00 AM Eastern (14:00 UTC - after DST change on March 9)
        // Note: March 9, 2025 at 2 AM EDT becomes 3 AM EDT; clocks spring forward

        Instant startUTC = ZonedDateTime.of(
            LocalDateTime.of(2025, 3, 7, 10, 0, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        AssignMembershipRequest request = new AssignMembershipRequest(
            planId,
            startUTC
        );

        Membership membership = membershipService.assignMembership(memberId, request);

        // Expected expiry: April 6, 2025, 10:00 AM Eastern
        // After DST change, this is 14:00 UTC (not 15:00)
        Instant expectedExpiry = ZonedDateTime.of(
            LocalDateTime.of(2025, 4, 6, 10, 0, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        assertThat(membership.getExpiresAt()).isEqualTo(expectedExpiry);
        // Verify date difference is 30 days (even accounting for DST shift)
        long daysBetween = ChronoUnit.DAYS.between(
            membership.getStartsAt(),
            membership.getExpiresAt()
        );
        assertThat(daysBetween).isEqualTo(30);
    }

    @Test
    void test_expiry_computation_simple_30_days() throws Exception {
        // Start on Jan 15 at 14:30 EST (America/New_York)
        Instant startTime = ZonedDateTime.of(
            LocalDateTime.of(2025, 1, 15, 14, 30, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        AssignMembershipRequest request = new AssignMembershipRequest(
            planId,
            startTime
        );

        Membership membership = membershipService.assignMembership(memberId, request);

        // Expected: Feb 14 at 14:30 EST (30 days later, same time)
        Instant expectedExpiry = ZonedDateTime.of(
            LocalDateTime.of(2025, 2, 14, 14, 30, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        assertThat(membership.getExpiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    void test_expiry_computation_leap_year() throws Exception {
        // Start on Feb 1, 2024 (leap year) at 10:00 EST, add 30 days
        Instant startTime = ZonedDateTime.of(
            LocalDateTime.of(2024, 2, 1, 10, 0, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        AssignMembershipRequest request = new AssignMembershipRequest(
            planId,
            startTime
        );

        Membership membership = membershipService.assignMembership(memberId, request);

        // Expected: Mar 2 at 10:00 EST (Feb 1 + 30 days, but Feb has 29 in 2024)
        Instant expectedExpiry = ZonedDateTime.of(
            LocalDateTime.of(2024, 3, 2, 10, 0, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        assertThat(membership.getExpiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    void test_expiry_computation_month_boundary() throws Exception {
        // Start on Jan 31 at 10:00 EST, add 30 days
        Instant startTime = ZonedDateTime.of(
            LocalDateTime.of(2025, 1, 31, 10, 0, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        AssignMembershipRequest request = new AssignMembershipRequest(
            planId,
            startTime
        );

        Membership membership = membershipService.assignMembership(memberId, request);

        // Expected: Mar 2 at 10:00 EST (Jan 31 + 30 days, Feb has 28 in 2025)
        Instant expectedExpiry = ZonedDateTime.of(
            LocalDateTime.of(2025, 3, 2, 10, 0, 0),
            ZoneId.of("America/New_York")
        ).toInstant();

        assertThat(membership.getExpiresAt()).isEqualTo(expectedExpiry);
    }
}
