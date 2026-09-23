package com.studio.booking.membership.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MembershipCancellationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("studio.timezone", () -> "UTC");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MembershipPlanRepository planRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private Clock clock;

    private UUID memberId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        planRepository.deleteAll();
        memberRepository.deleteAll();

        // Create test member
        Member member = new Member("test@example.com", "Test User", "555-1234", "ACTIVE", clock);
        Member saved = memberRepository.save(member);
        memberId = saved.getId();

        // Create test plan
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
    void test_ac11_cancel_pending_membership_sets_status_cancelled() throws Exception {
        Instant now = Instant.now(clock);
        Membership membership = new Membership(
            memberId,
            planId,
            "PENDING",
            false,
            10,
            10,
            now.plus(10, ChronoUnit.DAYS),
            now.plus(40, ChronoUnit.DAYS)
        );
        Membership saved = membershipRepository.save(membership);

        MvcResult result = mockMvc.perform(delete("/api/v1/members/" + memberId + "/memberships/" + saved.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        MembershipResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipResponse.class
        );

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void test_ac12_cancel_active_membership_returns_409_membership_not_cancellable() throws Exception {
        Instant now = Instant.now(clock);
        Membership membership = new Membership(
            memberId,
            planId,
            "ACTIVE",
            false,
            10,
            10,
            now.minus(10, ChronoUnit.DAYS),
            now.plus(20, ChronoUnit.DAYS)
        );
        Membership saved = membershipRepository.save(membership);

        MvcResult result = mockMvc.perform(delete("/api/v1/members/" + memberId + "/memberships/" + saved.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo("MEMBERSHIP_NOT_CANCELLABLE");
        assertThat(error.detail()).contains("ACTIVE");
    }

    @Test
    void test_ac13_cancel_expired_or_cancelled_returns_same_409() throws Exception {
        Instant now = Instant.now(clock);

        // Test EXPIRED
        Membership expiredMembership = new Membership(
            memberId,
            planId,
            "EXPIRED",
            false,
            10,
            10,
            now.minus(40, ChronoUnit.DAYS),
            now.minus(10, ChronoUnit.DAYS)
        );
        Membership savedExpired = membershipRepository.save(expiredMembership);

        MvcResult result = mockMvc.perform(delete("/api/v1/members/" + memberId + "/memberships/" + savedExpired.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo("MEMBERSHIP_NOT_CANCELLABLE");
        assertThat(error.detail()).contains("EXPIRED");

        // Test CANCELLED
        Membership cancelledMembership = new Membership(
            memberId,
            planId,
            "CANCELLED",
            false,
            10,
            10,
            now.minus(60, ChronoUnit.DAYS),
            now.minus(30, ChronoUnit.DAYS)
        );
        Membership savedCancelled = membershipRepository.save(cancelledMembership);

        MvcResult result2 = mockMvc.perform(delete("/api/v1/members/" + memberId + "/memberships/" + savedCancelled.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error2 = objectMapper.readValue(
            result2.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error2.code()).isEqualTo("MEMBERSHIP_NOT_CANCELLABLE");
        assertThat(error2.detail()).contains("CANCELLED");
    }

    @Test
    void test_ac14_after_cancel_pending_can_assign_new_without_already_queued() throws Exception {
        Instant now = Instant.now(clock);

        // Create an ACTIVE membership
        Membership activeMembership = new Membership(
            memberId,
            planId,
            "ACTIVE",
            false,
            10,
            10,
            now.minus(10, ChronoUnit.DAYS),
            now.plus(20, ChronoUnit.DAYS)
        );
        membershipRepository.save(activeMembership);

        // Create a PENDING membership to cancel
        Membership pendingMembership = new Membership(
            memberId,
            planId,
            "PENDING",
            false,
            10,
            10,
            now.plus(20, ChronoUnit.DAYS),
            now.plus(50, ChronoUnit.DAYS)
        );
        Membership savedPending = membershipRepository.save(pendingMembership);

        // Cancel the PENDING membership
        mockMvc.perform(delete("/api/v1/members/" + memberId + "/memberships/" + savedPending.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        // Now assign a new membership (this should not fail with MEMBERSHIP_ALREADY_QUEUED)
        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            now.plus(20, ChronoUnit.DAYS)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipResponse.class
        );

        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void test_ac15_cancel_advances_version_and_updated_at() throws Exception {
        Instant now = Instant.now(clock);
        Membership membership = new Membership(
            memberId,
            planId,
            "PENDING",
            false,
            10,
            10,
            now.plus(10, ChronoUnit.DAYS),
            now.plus(40, ChronoUnit.DAYS)
        );
        Membership saved = membershipRepository.save(membership);

        long versionBefore = saved.getVersion();
        Instant updatedAtBefore = saved.getUpdatedAt();

        // Cancel the membership
        mockMvc.perform(delete("/api/v1/members/" + memberId + "/memberships/" + saved.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        // Re-read from repository
        Membership reread = membershipRepository.findById(saved.getId()).orElseThrow();

        assertThat(reread.getVersion()).isGreaterThan(versionBefore);
        assertThat(reread.getUpdatedAt()).isGreaterThanOrEqualTo(updatedAtBefore);
    }
}
