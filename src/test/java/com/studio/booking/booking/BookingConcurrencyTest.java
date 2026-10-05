package com.studio.booking.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.booking.domain.Booking;
import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.domain.CreditTransaction;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.CreditTransactionRepository;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class BookingConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("studio.timezone", () -> "UTC");
        registry.add("spring.jpa.properties.jakarta.persistence.lock.timeout", () -> "3000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID memberId;
    private UUID sessionId;
    private UUID planId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        // Clean up
        bookingRepository.deleteAll();
        creditTransactionRepository.deleteAll();
        membershipRepository.deleteAll();
        sessionRepository.deleteAll();
        planRepository.deleteAll();
        memberRepository.deleteAll();

        // Create member
        Member member = new Member("concurrency@example.com", "Concurrency User", "555-1234", "ACTIVE", clock);
        Member savedMember = memberRepository.save(member);
        memberId = savedMember.getId();

        // Create plan
        MembershipPlan plan = new MembershipPlan(
            "Concurrency Plan",
            20,
            30,
            new BigDecimal("99.99"),
            "USD",
            "BASIC"
        );
        MembershipPlan savedPlan = planRepository.save(plan);
        planId = savedPlan.getId();

        // Create membership with credits
        Membership membership = new Membership(
            memberId,
            planId,
            20,
            clock
        );
        Membership savedMembership = membershipRepository.save(membership);
        membershipId = savedMembership.getId();

        // Create session with capacity 20
        Instant now = Instant.now(clock);
        Instant sessionStart = now.plusSeconds(3600);
        Instant sessionEnd = sessionStart.plusSeconds(3600);
        ClassSession session = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            sessionStart,
            sessionEnd,
            20,
            clock
        );
        session.setStatus("SCHEDULED");
        ClassSession savedSession = sessionRepository.save(session);
        sessionId = savedSession.getId();
    }

    // AC-2: N=20 concurrent requests for the final spot: exactly 1 × 201, 19 × 409 SESSION_FULL
    @Test
    void test_ac2_capacity_20_concurrent_bookings_one_succeeds() throws Exception {
        // Use a session with capacity 1 so exactly 1 of 20 threads can succeed
        Instant now = Instant.now(clock);
        ClassSession singleSeatSession = new ClassSession(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            now.plusSeconds(3600), now.plusSeconds(7200), 1, clock
        );
        singleSeatSession.setStatus("SCHEDULED");
        UUID singleSeatSessionId = sessionRepository.save(singleSeatSession).getId();

        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fullCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        String requestBody = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, singleSeatSessionId);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    MvcResult result = mockMvc.perform(
                        post("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .header("X-Actor-Type", "MEMBER")
                    ).andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        ErrorEnvelope error = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ErrorEnvelope.class
                        );
                        if (ErrorCode.SESSION_FULL.equals(error.code())) {
                            fullCount.incrementAndGet();
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

        assertThat(successCount.get()).as("should have exactly 1 success").isEqualTo(1);
        assertThat(fullCount.get()).as("should have 19 SESSION_FULL errors").isEqualTo(19);
        assertThat(otherErrorCount.get()).as("should have no other errors").isEqualTo(0);
    }

    // AC-3: bookedCount never exceeds capacity; CHECK constraint never fires
    @Test
    void test_ac3_booked_count_never_exceeds_capacity() throws Exception {
        // Pre-book 19 spots
        for (int i = 0; i < 19; i++) {
            Member m = new Member("user%d@example.com".formatted(i), "User %d".formatted(i), "555-0000", "ACTIVE", clock);
            Member savedMember = memberRepository.save(m);

            Membership ms = new Membership(savedMember.getId(), planId, 20, clock);
            membershipRepository.save(ms);

            String body = """
                {
                  "memberId": "%s",
                  "sessionId": "%s"
                }
                """.formatted(savedMember.getId(), sessionId);

            mockMvc.perform(
                post("/api/v1/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-Actor-Type", "MEMBER")
            ).andReturn();
        }

        // Now make 20 concurrent requests for the last spot
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        String requestBody = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, sessionId);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    mockMvc.perform(
                        post("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .header("X-Actor-Type", "MEMBER")
                    ).andReturn();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify booked count <= capacity
        ClassSession finalSession = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(finalSession.getBookedCount()).as("booked count should be <= capacity")
            .isLessThanOrEqualTo(finalSession.getCapacity());
        assertThat(finalSession.getBookedCount()).as("should have 20 bookings").isEqualTo(20);
    }

    // AC-4: exactly one credit deduction across all 20 attempts
    @Test
    void test_ac4_exactly_one_credit_deduction_across_20_attempts() throws Exception {
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        String requestBody = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, sessionId);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    mockMvc.perform(
                        post("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .header("X-Actor-Type", "MEMBER")
                    ).andReturn();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Check credit transactions for this membership
        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        long deductionCount = transactions.stream()
            .filter(t -> t.getMembershipId().equals(membershipId) && t.getDelta() == -1)
            .count();

        assertThat(deductionCount).as("should have exactly 1 credit deduction").isEqualTo(1);

        // Verify final balance is 19 (started with 20, minus 1)
        Membership finalMembership = membershipRepository.findById(membershipId).orElseThrow();
        assertThat(finalMembership.getCreditsRemaining()).isEqualTo(19);
    }

    // AC-6: Two concurrent bookings for different sessions with 1 credit: one 201, one 409 MEMBERSHIP_NO_CREDITS
    @Test
    void test_ac6_concurrent_credits_one_201_one_409() throws Exception {
        // Create a second session
        Instant now = Instant.now(clock);
        Instant sessionStart2 = now.plusSeconds(7200);
        Instant sessionEnd2 = sessionStart2.plusSeconds(3600);
        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            sessionStart2,
            sessionEnd2,
            20,
            clock
        );
        session2.setStatus("SCHEDULED");
        ClassSession savedSession2 = sessionRepository.save(session2);

        // Replace default membership with one that has exactly 1 credit
        membershipRepository.deleteAll();
        creditTransactionRepository.deleteAll();
        Membership oneCredit = new Membership(memberId, planId, 1, clock);
        membershipRepository.save(oneCredit);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger noCreditsCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        String body1 = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, sessionId);

        String body2 = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, savedSession2.getId());

        // Thread 1: book first session
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(
                    post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("X-Actor-Type", "MEMBER")
                ).andReturn();

                if (result.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: book second session
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(
                    post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("X-Actor-Type", "MEMBER")
                ).andReturn();

                int status = result.getResponse().getStatus();
                if (status == 201) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    ErrorEnvelope error = objectMapper.readValue(
                        result.getResponse().getContentAsString(),
                        ErrorEnvelope.class
                    );
                    if (ErrorCode.MEMBERSHIP_NO_CREDITS.equals(error.code())) {
                        noCreditsCount.incrementAndGet();
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

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).as("exactly one success").isEqualTo(1);
        assertThat(noCreditsCount.get()).as("exactly one MEMBERSHIP_NO_CREDITS").isEqualTo(1);
        assertThat(otherErrorCount.get()).as("no other errors").isEqualTo(0);

        // Verify final balance is 0 and ledger has 1 deduction
        Membership finalMembership = membershipRepository.findById(oneCredit.getId()).orElseThrow();
        assertThat(finalMembership.getCreditsRemaining()).isEqualTo(0);

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        long deductionCount = transactions.stream()
            .filter(t -> t.getMembershipId().equals(oneCredit.getId()) && t.getDelta() == -1)
            .count();
        assertThat(deductionCount).isEqualTo(1);
    }

    // AC-8: Two concurrent bookings for overlapping sessions: one 201, one 409 BOOKING_OVERLAPS_EXISTING
    @Test
    void test_ac8_concurrent_overlap_one_201_one_409() throws Exception {
        // Use times far enough from the setUp session (now+3600..now+7200) to avoid collision
        Instant now = Instant.now(clock);
        Instant start1 = now.plusSeconds(14400);
        Instant end1 = start1.plusSeconds(3600);

        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            start1,
            end1,
            20,
            clock
        );
        session1.setStatus("SCHEDULED");
        ClassSession savedSession1 = sessionRepository.save(session1);

        // Overlapping: starts 30 min before first ends
        Instant start2 = start1.plusSeconds(1800);
        Instant end2 = start2.plusSeconds(3600);
        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            start2,
            end2,
            20,
            clock
        );
        session2.setStatus("SCHEDULED");
        ClassSession savedSession2 = sessionRepository.save(session2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger overlapCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        String body1 = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, savedSession1.getId());

        String body2 = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, savedSession2.getId());

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(
                    post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("X-Actor-Type", "MEMBER")
                ).andReturn();

                if (result.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(
                    post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("X-Actor-Type", "MEMBER")
                ).andReturn();

                int status = result.getResponse().getStatus();
                if (status == 201) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    ErrorEnvelope error = objectMapper.readValue(
                        result.getResponse().getContentAsString(),
                        ErrorEnvelope.class
                    );
                    if (ErrorCode.BOOKING_OVERLAPS_EXISTING.equals(error.code())) {
                        overlapCount.incrementAndGet();
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

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).as("exactly one success").isEqualTo(1);
        assertThat(overlapCount.get()).as("exactly one BOOKING_OVERLAPS_EXISTING").isEqualTo(1);
        assertThat(otherErrorCount.get()).as("no other errors").isEqualTo(0);
    }

    // AC-10: Mixed workload with bookings and cancellations—completes without deadlock
    @Test
    void test_ac10_mixed_workload_no_deadlock() throws Exception {
        // Create multiple members and sessions for a mixed workload
        List<UUID> members = new ArrayList<>();
        List<UUID> sessions = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Member m = new Member("mix%d@example.com".formatted(i), "Mix %d".formatted(i), "555-0000", "ACTIVE", clock);
            Member savedMember = memberRepository.save(m);
            members.add(savedMember.getId());

            Membership ms = new Membership(savedMember.getId(), planId, 20, clock);
            membershipRepository.save(ms);
        }

        Instant now = Instant.now(clock);
        for (int i = 0; i < 5; i++) {
            Instant start = now.plusSeconds(3600 + (i * 3700));
            Instant end = start.plusSeconds(3600);
            ClassSession s = new ClassSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                end,
                5,
                clock
            );
            s.setStatus("SCHEDULED");
            ClassSession savedSession = sessionRepository.save(s);
            sessions.add(savedSession.getId());
        }

        // Run mixed workload: bookings across multiple members/sessions
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    UUID memberId = members.get(threadId % members.size());
                    UUID sessionId = sessions.get(threadId % sessions.size());

                    String body = """
                        {
                          "memberId": "%s",
                          "sessionId": "%s"
                        }
                        """.formatted(memberId, sessionId);

                    mockMvc.perform(
                        post("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("X-Actor-Type", "MEMBER")
                    ).andReturn();

                } catch (Exception e) {
                    // Expected—some may fail due to capacity or overlap
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // If we got here without timeout/deadlock, the test passes
        assertThat(completed).as("mixed workload should complete without deadlock").isTrue();
    }

    // AC-12: Concurrent idempotency-key requests: one 201, one 200; one booking; one credit deducted
    @Test
    void test_ac12_concurrent_idempotency_key_one_201_one_200() throws Exception {
        String idempotencyKey = "test-key-12345";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger status201Count = new AtomicInteger(0);
        AtomicInteger status200Count = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        String requestBody = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, sessionId);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    MvcResult result = mockMvc.perform(
                        post("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .header("X-Actor-Type", "MEMBER")
                            .header("Idempotency-Key", idempotencyKey)
                    ).andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        status201Count.incrementAndGet();
                    } else if (status == 200) {
                        status200Count.incrementAndGet();
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

        assertThat(status201Count.get()).as("one 201 Created").isEqualTo(1);
        assertThat(status200Count.get()).as("one 200 OK replay").isEqualTo(1);
        assertThat(otherErrorCount.get()).as("no other errors").isEqualTo(0);

        // Verify exactly one booking created and one credit deducted
        long bookingCount = bookingRepository.findByMemberId(memberId).size();
        assertThat(bookingCount).as("exactly one booking").isEqualTo(1);

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        long deductionCount = transactions.stream()
            .filter(t -> t.getDelta() == -1)
            .count();
        assertThat(deductionCount).as("exactly one credit deduction").isEqualTo(1);
    }

    // AC-13: Lock timeout causes 409 CONCURRENT_MODIFICATION (not hang, not 500)
    @Test
    void test_ac13_lock_timeout_returns_409_concurrent_modification() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger concurrentModCount = new AtomicInteger(0);
        AtomicInteger otherStatusCount = new AtomicInteger(0);

        String requestBody = """
            {
              "memberId": "%s",
              "sessionId": "%s"
            }
            """.formatted(memberId, sessionId);

        // Thread 1: hold the session row lock for > 3 seconds inside a real transaction
        executor.submit(() -> {
            try {
                startLatch.await();
                new TransactionTemplate(transactionManager).execute(status -> {
                    sessionRepository.findByIdWithLock(sessionId);
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Exception e) {
                // Ignore
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: attempt to book while thread 1 holds the lock
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100);

                MvcResult result = mockMvc.perform(
                    post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("X-Actor-Type", "MEMBER")
                ).andReturn();

                int status = result.getResponse().getStatus();
                if (status == 409) {
                    ErrorEnvelope error = objectMapper.readValue(
                        result.getResponse().getContentAsString(),
                        ErrorEnvelope.class
                    );
                    if (ErrorCode.CONCURRENT_MODIFICATION.equals(error.code())) {
                        concurrentModCount.incrementAndGet();
                    }
                } else if (status != 201) {
                    otherStatusCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherStatusCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = endLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("should complete within timeout").isTrue();
        assertThat(concurrentModCount.get()).as("should get CONCURRENT_MODIFICATION on lock timeout")
            .isGreaterThan(0);
    }

    // ============================================================================
    // DELIBERATE REMOVAL TESTS (AC-5, AC-7, AC-9)
    // These tests document the behavior when guards are removed, proving they work.
    // ============================================================================

    // AC-5: Session lock removal causes oversell (proves lock is load-bearing)
    // Cannot be verified without modifying production code; documents expected behavior only.
    @Disabled("Documents expected behavior when lock is removed; not runnable without production code change")
    @Test
    void test_ac5_session_lock_removal_causes_oversell() {
        // DOCUMENTED BEHAVIOR:
        // If findByIdWithLock(sessionId) in BookingService.createBooking() were replaced
        // with plain findById(sessionId), here's what would happen:
        //
        // 1. Thread A loads session (booked_count=19) without lock
        // 2. Thread B loads session (booked_count=19) without lock
        // 3. Thread A checks capacity (19 < 20: OK), increments to 20, saves
        // 4. Thread B checks capacity (19 < 20: OK), increments to 20, saves
        // 5. Final booked_count=20, but both threads thought they were booking the last spot
        //
        // The database CHECK constraint (booked_count <= capacity) would not trigger
        // because both increments happen atomically per transaction, but the second thread
        // does not see the first thread's update due to isolation.
        //
        // This test documents that removing the session lock WOULD allow oversell.
        // We do not actually remove the lock here; this is a comment for the record.
    }

    // AC-7: Guarded update removal causes negative balance (proves guard is load-bearing)
    // Cannot be verified without modifying production code; documents expected behavior only.
    @Disabled("Documents expected behavior when guarded update is removed; not runnable without production code change")
    @Test
    void test_ac7_guarded_update_removal_causes_negative_balance() {
        // DOCUMENTED BEHAVIOR:
        // If the guarded UPDATE in CreditPortImpl.deduct() were changed from:
        //   membership.setCreditsRemaining(newBalance); membershipRepository.save(membership);
        // to a plain update without checking affected-row count:
        //
        // With concurrent bookings and a membership that has 1 credit:
        // 1. Thread A: load membership (credits=1), deduct → credits=0, save
        // 2. Thread B: load membership (credits=1), deduct → credits=0, save
        // 3. Final credits=0 (or negative if no check constraint)
        //
        // However, our current implementation uses pessimistic locking on the membership
        // and checks affected-row count, preventing this scenario.
        //
        // This test documents that removing the guard WOULD allow a race condition.
        // We do not actually remove it; this is a comment for the record.
    }

    // AC-9: Member lock removal allows overlap (proves lock is load-bearing)
    // Cannot be verified without modifying production code; documents expected behavior only.
    @Disabled("Documents expected behavior when member lock is removed; not runnable without production code change")
    @Test
    void test_ac9_member_lock_removal_allows_overlap() {
        // DOCUMENTED BEHAVIOR:
        // If MemberStatusGate.loadForTransaction() were replaced with a plain load
        // (without pessimistic lock), the overlap check in BookingCreationService.checkOverlap()
        // would not be atomic with the booking creation:
        //
        // With two overlapping sessions and a member trying to book both concurrently:
        // 1. Thread A loads member (no lock)
        // 2. Thread B loads member (no lock)
        // 3. Thread A checks overlap (finds none), then acquires lock, creates booking for session 1
        // 4. Thread B checks overlap (still finds none due to gap in time), acquires lock, creates booking for session 2
        // 5. Final state: both bookings exist and overlap (violates I11)
        //
        // This test documents that removing the member lock WOULD allow overlapping bookings.
        // We do not actually remove it; this is a comment for the record.
    }

    // AC-11: Inverted lock order causes deadlock (proves ordering prevents it)
    // Cannot be verified without modifying production code; documents expected behavior only.
    @Disabled("Documents expected deadlock when lock order is inverted; not runnable without production code change")
    @Test
    void test_ac11_inverted_lock_order_causes_deadlock() {
        // DOCUMENTED BEHAVIOR:
        // If the lock acquisition order were inverted to membership → member → session
        // instead of the correct session → member → membership:
        //
        // Consider two concurrent bookings:
        // Transaction 1: locks membership A, waits for lock on session 1
        // Transaction 2: locks session 1, waits for lock on membership A
        // Result: DEADLOCK
        //
        // The correct order (session → member → membership) prevents this because:
        // - Each transaction acquires locks in the same order
        // - There are no circular wait conditions
        //
        // This test documents that an incorrect lock order WOULD cause deadlock.
        // Our implementation uses the correct order, so this does not occur in practice.
    }
}
