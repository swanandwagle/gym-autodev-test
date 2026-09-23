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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MembershipAssignmentIntegrationTest {

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
    void test_ac1_no_membership_no_startsAt_creates_active_starting_now() throws Exception {
        Instant beforeCall = Instant.now(clock);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

        MembershipResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipResponse.class
        );

        Instant afterCall = Instant.now(clock);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.startsAt()).isGreaterThanOrEqualTo(beforeCall).isLessThanOrEqualTo(afterCall);
        assertThat(response.unlimited()).isFalse();
        assertThat(response.creditsInitial()).isEqualTo(10);
        assertThat(response.creditsRemaining()).isEqualTo(10);
    }

    @Test
    void test_ac2_future_startsAt_creates_pending() throws Exception {
        Instant futureStart = Instant.now(clock).plus(1, ChronoUnit.HOURS);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            futureStart
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
        assertThat(response.startsAt()).isEqualTo(futureStart);
    }

    @Test
    void test_ac3_active_membership_no_startsAt_creates_pending_at_expiry() throws Exception {
        Instant now = Instant.now(clock);
        Instant activeExpiry = now.plus(30, ChronoUnit.DAYS);

        // Create active membership
        Membership active = new Membership(
            memberId,
            planId,
            "ACTIVE",
            false,
            10,
            10,
            now,
            activeExpiry
        );
        membershipRepository.save(active);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            null
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
        assertThat(response.startsAt()).isEqualTo(activeExpiry);
    }

    @Test
    void test_ac4_startsAt_exactly_at_active_expiry_succeeds() throws Exception {
        Instant now = Instant.now(clock);
        Instant activeExpiry = now.plus(30, ChronoUnit.DAYS);

        // Create active membership
        Membership active = new Membership(
            memberId,
            planId,
            "ACTIVE",
            false,
            10,
            10,
            now,
            activeExpiry
        );
        membershipRepository.save(active);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            activeExpiry
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
        assertThat(response.startsAt()).isEqualTo(activeExpiry);
    }

    @Test
    void test_ac5_startsAt_one_second_before_expiry_returns_409() throws Exception {
        Instant now = Instant.now(clock);
        Instant activeExpiry = now.plus(30, ChronoUnit.DAYS);
        Instant oneSecondBefore = activeExpiry.minusSeconds(1);

        // Create active membership
        Membership active = new Membership(
            memberId,
            planId,
            "ACTIVE",
            false,
            10,
            10,
            now,
            activeExpiry
        );
        membershipRepository.save(active);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            oneSecondBefore
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBERSHIP_START_BEFORE_CURRENT_EXPIRY);
    }

    @Test
    void test_ac6_third_membership_returns_409_already_queued() throws Exception {
        Instant now = Instant.now(clock);
        Instant activeExpiry = now.plus(30, ChronoUnit.DAYS);
        Instant pendingStart = activeExpiry.plus(1, ChronoUnit.DAYS);

        // Create active membership
        Membership active = new Membership(
            memberId,
            planId,
            "ACTIVE",
            false,
            10,
            10,
            now,
            activeExpiry
        );
        membershipRepository.save(active);

        // Create pending membership
        Membership pending = new Membership(
            memberId,
            planId,
            "PENDING",
            false,
            10,
            10,
            pendingStart,
            pendingStart.plus(30, ChronoUnit.DAYS)
        );
        membershipRepository.save(pending);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            pendingStart.plus(31, ChronoUnit.DAYS)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBERSHIP_ALREADY_QUEUED);
    }

    @Test
    void test_ac7_inactive_plan_returns_409_plan_inactive() throws Exception {
        // Deactivate plan
        MembershipPlan plan = planRepository.findById(planId).orElseThrow();
        plan.deactivate();
        planRepository.save(plan);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.PLAN_INACTIVE);
    }

    @Test
    void test_ac8_unlimited_plan_produces_unlimited_true_null_credits() throws Exception {
        // Create unlimited plan (classCredits = null)
        MembershipPlan unlimitedPlan = new MembershipPlan(
            "Unlimited Plan",
            null,
            30,
            new BigDecimal("199.99"),
            "USD",
            "PREMIUM"
        );
        MembershipPlan savedPlan = planRepository.save(unlimitedPlan);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            savedPlan.getId(),
            null
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

        assertThat(response.unlimited()).isTrue();
        assertThat(response.creditsInitial()).isNull();
        assertThat(response.creditsRemaining()).isNull();
    }

    @Test
    void test_ac9_credit_plan_produces_credits_equal_plan_credits() throws Exception {
        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            null
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

        assertThat(response.unlimited()).isFalse();
        assertThat(response.creditsInitial()).isEqualTo(10);
        assertThat(response.creditsRemaining()).isEqualTo(10);
    }

    @Test
    void test_ac10_suspended_member_can_be_assigned() throws Exception {
        // Suspend member
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.suspendStatus("Test suspension", clock);
        memberRepository.save(member);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            null
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

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void test_ac11_startsAt_6min_past_422_out_of_range() throws Exception {
        Instant sixMinutesAgo = Instant.now(clock).minusSeconds(360);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            sixMinutesAgo
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.OUT_OF_RANGE);
    }

    @Test
    void test_ac11_startsAt_4min_past_accepted() throws Exception {
        Instant fourMinutesAgo = Instant.now(clock).minusSeconds(240);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            fourMinutesAgo
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

        assertThat(response.startsAt()).isEqualTo(fourMinutesAgo);
    }

    @Test
    void test_ac14_unknown_memberId_returns_404() throws Exception {
        UUID unknownMemberId = UUID.randomUUID();

        AssignMembershipRequest request = new AssignMembershipRequest(
            unknownMemberId,
            planId,
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + unknownMemberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void test_ac14_unknown_planId_returns_404() throws Exception {
        UUID unknownPlanId = UUID.randomUUID();

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            unknownPlanId,
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND);
    }
}
