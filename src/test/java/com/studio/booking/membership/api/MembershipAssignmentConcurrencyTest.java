package com.studio.booking.membership.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MembershipAssignmentConcurrencyTest {

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

        Member member = new Member("concurrent@example.com", "Concurrent User", "555-1234", "ACTIVE", clock);
        Member saved = memberRepository.save(member);
        memberId = saved.getId();

        MembershipPlan plan = new MembershipPlan(
            "Concurrent Plan",
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
    void test_ac12_concurrent_assignments_one_201_rest_409() throws Exception {
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        AssignMembershipRequest request = new AssignMembershipRequest(
            memberId,
            planId,
            null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    MvcResult result = mockMvc.perform(post("/api/v1/members/" + memberId + "/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                        .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        ErrorEnvelope error = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ErrorEnvelope.class
                        );
                        if (ErrorCode.MEMBERSHIP_ALREADY_QUEUED.equals(error.code())) {
                            conflictCount.incrementAndGet();
                        } else {
                            otherErrorCount.incrementAndGet();
                        }
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(numThreads - 1);
        assertThat(otherErrorCount.get()).isEqualTo(0);

        long membershipCount = membershipRepository.count();
        assertThat(membershipCount).isEqualTo(1);
    }
}
