package com.studio.booking.membership.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.application.CreditPort;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.CreditTransactionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreditLedgerControllerIntegrationTest {

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
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private MembershipPlanRepository planRepository;
    @Autowired private CreditTransactionRepository creditTransactionRepository;
    @Autowired private CreditPort creditPort;
    @Autowired private Clock clock;

    private UUID memberId;
    private UUID membershipId;
    private UUID membershipIdUnlimited;

    @BeforeEach
    void setUp() {
        creditTransactionRepository.deleteAll();
        membershipRepository.deleteAll();
        planRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = new Member("test@example.com", "Test User", "555-1234", "ACTIVE", clock);
        Member saved = memberRepository.save(member);
        memberId = saved.getId();

        // Credit-based plan
        MembershipPlan plan = new MembershipPlan(
            "Test Plan",
            10,
            10,
            new BigDecimal("99.99"),
            "USD",
            "BASIC"
        );
        MembershipPlan savedPlan = planRepository.save(plan);

        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, savedPlan.getId(), "ACTIVE", false,
            10, 10, startsAt, expiresAt
        );
        Membership savedMembership = membershipRepository.save(membership);
        membershipId = savedMembership.getId();

        // Unlimited plan
        MembershipPlan unlimitedPlan = new MembershipPlan(
            "Unlimited Plan",
            null,
            10,
            new BigDecimal("199.99"),
            "USD",
            "PREMIUM"
        );
        MembershipPlan savedUnlimitedPlan = planRepository.save(unlimitedPlan);

        Membership unlimitedMembership = new Membership(
            memberId, savedUnlimitedPlan.getId(), "ACTIVE", true,
            null, null, startsAt, expiresAt
        );
        Membership savedUnlimitedMembership = membershipRepository.save(unlimitedMembership);
        membershipIdUnlimited = savedUnlimitedMembership.getId();
    }

    // AC-15: Ledger read returns rows oldest-first with reconciles: true
    @Test
    void test_ac15_ledger_read_returns_oldest_first_reconciles_true() throws Exception {
        // Add some transactions
        creditPort.deduct(membershipId, "BOOKING");
        creditPort.deduct(membershipId, "BOOKING");
        creditPort.refund(membershipId, "CANCEL_REFUND");

        MvcResult result = mockMvc.perform(
            get("/api/v1/memberships/" + membershipId + "/ledger")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn();

        String content = result.getResponse().getContentAsString();
        CreditLedgerResponse response = objectMapper.readValue(content, CreditLedgerResponse.class);

        assertThat(response.content()).hasSize(3);
        assertThat(response.reconciles()).isTrue();

        // Verify oldest-first order (by creation time)
        int prevBalance = response.content().get(0).balanceAfter() - response.content().get(0).delta();
        for (CreditLedgerResponse.Entry entry : response.content()) {
            assertThat(entry.delta() + prevBalance).isEqualTo(entry.balanceAfter());
            prevBalance = entry.balanceAfter();
        }
    }

    // AC-16: Ledger read for unlimited membership returns empty content with null credit figures
    @Test
    void test_ac16_ledger_read_unlimited_empty_content_null_figures() throws Exception {
        // Add transaction to unlimited membership (should not create ledger)
        creditPort.deduct(membershipIdUnlimited, "BOOKING");

        MvcResult result = mockMvc.perform(
            get("/api/v1/memberships/" + membershipIdUnlimited + "/ledger")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn();

        String content = result.getResponse().getContentAsString();
        CreditLedgerResponse response = objectMapper.readValue(content, CreditLedgerResponse.class);

        assertThat(response.content()).isEmpty();
        assertThat(response.creditsInitial()).isNull();
        assertThat(response.creditsRemaining()).isNull();
    }

    // AC-17: Ledger read on unknown membership returns 404
    @Test
    void test_ac17_ledger_read_unknown_membership_404() throws Exception {
        UUID unknownId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(
            get("/api/v1/memberships/" + unknownId + "/ledger")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound())
            .andReturn();

        String content = result.getResponse().getContentAsString();
        ErrorEnvelope envelope = objectMapper.readValue(content, ErrorEnvelope.class);

        assertThat(envelope.code()).isEqualTo(ErrorCode.MEMBERSHIP_NOT_FOUND.name());
    }
}
