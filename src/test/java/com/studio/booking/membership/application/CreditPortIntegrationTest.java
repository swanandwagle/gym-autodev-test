package com.studio.booking.membership.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.domain.CreditTransaction;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.CreditTransactionRepository;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreditPortIntegrationTest {

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

    @Autowired private CreditPort creditPort;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private MembershipPlanRepository planRepository;
    @Autowired private CreditTransactionRepository creditTransactionRepository;
    @Autowired private Clock clock;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID memberId;
    private UUID planId;
    private UUID planIdUnlimited;

    @BeforeEach
    void setUp() {
        creditTransactionRepository.deleteAll();
        membershipRepository.deleteAll();
        planRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = new Member("test@example.com", "Test User", "555-1234", "ACTIVE", clock);
        Member saved = memberRepository.save(member);
        memberId = saved.getId();

        // Credit-based plan: 10 credits
        MembershipPlan plan = new MembershipPlan(
            "Test Plan",
            10,
            10,
            new BigDecimal("99.99"),
            "USD",
            "BASIC"
        );
        MembershipPlan savedPlan = planRepository.save(plan);
        planId = savedPlan.getId();

        // Unlimited plan (null credits)
        MembershipPlan unlimitedPlan = new MembershipPlan(
            "Unlimited Plan",
            null,
            10,
            new BigDecimal("199.99"),
            "USD",
            "PREMIUM"
        );
        MembershipPlan savedUnlimitedPlan = planRepository.save(unlimitedPlan);
        planIdUnlimited = savedUnlimitedPlan.getId();
    }

    // AC-1: loadUsableForBooking returns membership when ACTIVE and within time window
    @Test
    void test_ac1_loadUsableForBooking_active_within_window() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 10, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        Optional<Membership> result = creditPort.loadUsableForBooking(memberId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    // AC-2: loadUsableForBooking uses exclusive upper bound for expiresAt
    // Tests with three points: before, at, and after expiry
    @Test
    void test_ac2_loadUsableForBooking_expiry_boundary_exclusive() {
        Instant testTime = Instant.now(clock);
        Instant expiresAt = testTime.plus(1, ChronoUnit.HOURS);
        Instant startsAt = expiresAt.minus(2, ChronoUnit.HOURS);

        // Test point 1: one second before expiry → should return
        {
            membershipRepository.deleteAll();
            Membership m = new Membership(
                memberId, planId, "ACTIVE", false,
                10, 10, startsAt, expiresAt
            );
            membershipRepository.save(m);

            // Current time is before expiry, so should be present
            Optional<Membership> result = creditPort.loadUsableForBooking(memberId);
            assertThat(result).isPresent();
        }

        // Test point 2: at exactly expiresAt → should NOT return (exclusive upper bound)
        {
            membershipRepository.deleteAll();
            Membership m = new Membership(
                memberId, planId, "ACTIVE", false,
                10, 10, startsAt, testTime
            );
            membershipRepository.save(m);

            // expiresAt is exactly now, should be empty (upper bound is exclusive)
            Optional<Membership> result = creditPort.loadUsableForBooking(memberId);
            assertThat(result).isEmpty();
        }

        // Test point 3: after expiry → should NOT return
        {
            membershipRepository.deleteAll();
            Membership m = new Membership(
                memberId, planId, "ACTIVE", false,
                10, 10, startsAt, testTime.minus(1, ChronoUnit.SECONDS)
            );
            membershipRepository.save(m);

            Optional<Membership> result = creditPort.loadUsableForBooking(memberId);
            assertThat(result).isEmpty();
        }
    }

    // AC-3: loadUsableForBooking returns empty for PENDING membership whose startsAt has not arrived
    @Test
    void test_ac3_loadUsableForBooking_pending_not_arrived() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.plus(1, ChronoUnit.HOURS);
        Instant expiresAt = startsAt.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "PENDING", false,
            10, 10, startsAt, expiresAt
        );
        membershipRepository.save(membership);

        Optional<Membership> result = creditPort.loadUsableForBooking(memberId);

        assertThat(result).isEmpty();
    }

    // AC-4: loadUsableForBooking acquires pessimistic lock
    @Test
    void test_ac4_pessimistic_lock_blocks_second_transaction() throws InterruptedException {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 10, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        TransactionTemplate tt = new TransactionTemplate(transactionManager);

        CountDownLatch firstTransactionStarted = new CountDownLatch(1);
        CountDownLatch secondTransactionCanProceed = new CountDownLatch(1);

        AtomicReference<Boolean> secondTransactionBlocked = new AtomicReference<>(false);

        Thread thread1 = new Thread(() -> {
            tt.execute(status -> {
                Optional<Membership> m = creditPort.loadUsableForBooking(memberId);
                assertThat(m).isPresent();
                firstTransactionStarted.countDown();
                try {
                    secondTransactionCanProceed.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        Thread thread2 = new Thread(() -> {
            try {
                firstTransactionStarted.await();
                long startTime = System.nanoTime();
                tt.execute(status -> {
                    long acquireTime = System.nanoTime() - startTime;
                    if (acquireTime > 100_000_000) { // More than 100ms
                        secondTransactionBlocked.set(true);
                    }
                    Optional<Membership> m = creditPort.loadUsableForBooking(memberId);
                    assertThat(m).isPresent();
                    return null;
                });
                secondTransactionCanProceed.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        thread1.start();
        thread2.start();

        thread1.join(5000);
        thread2.join(5000);

        assertThat(secondTransactionBlocked.get()).isTrue();
    }

    // AC-5: requireCredit() throws at zero balance; hasCredit() returns false without throwing
    @Test
    void test_ac5_requireCredit_zero_balance() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 0, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        assertThatThrownBy(() -> creditPort.requireCredit(saved.getId()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.MEMBERSHIP_NO_CREDITS);

        boolean hasCredit = creditPort.hasCredit(saved.getId());
        assertThat(hasCredit).isFalse();
    }

    // AC-6: Both return success for unlimited membership regardless of balance
    @Test
    void test_ac6_unlimited_membership_always_has_credit() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planIdUnlimited, "ACTIVE", true,
            null, null, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        // requireCredit should not throw
        creditPort.requireCredit(saved.getId());

        boolean hasCredit = creditPort.hasCredit(saved.getId());
        assertThat(hasCredit).isTrue();
    }

    // AC-7: deduct on credit-based membership reduces balance by 1 and writes ledger
    @Test
    void test_ac7_deduct_credit_based_reduces_balance_and_writes_ledger() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 5, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        creditPort.deduct(saved.getId(), "BOOKING");

        Membership updated = membershipRepository.findById(saved.getId()).get();
        assertThat(updated.getCreditsRemaining()).isEqualTo(4);

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);

        CreditTransaction txn = transactions.get(0);
        assertThat(txn.getMembershipId()).isEqualTo(saved.getId());
        assertThat(txn.getDelta()).isEqualTo(-1);
        assertThat(txn.getReason()).isEqualTo("BOOKING");
        assertThat(txn.getBalanceAfter()).isEqualTo(4);
    }

    // AC-8: deduct on unlimited membership changes nothing and writes no ledger row
    @Test
    void test_ac8_deduct_unlimited_no_change_no_ledger() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planIdUnlimited, "ACTIVE", true,
            null, null, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        creditPort.deduct(saved.getId(), "BOOKING");

        Membership updated = membershipRepository.findById(saved.getId()).get();
        assertThat(updated.getCreditsRemaining()).isNull();
        assertThat(updated.isUnlimited()).isTrue();

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        assertThat(transactions).isEmpty();
    }

    // AC-9: refund mirrors both cases
    @Test
    void test_ac9_refund_mirrors_deduct() {
        // Credit-based case
        {
            membershipRepository.deleteAll();
            creditTransactionRepository.deleteAll();

            Instant now = Instant.now(clock);
            Instant startsAt = now.minus(1, ChronoUnit.HOURS);
            Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

            Membership membership = new Membership(
                memberId, planId, "ACTIVE", false,
                10, 5, startsAt, expiresAt
            );
            Membership saved = membershipRepository.save(membership);

            creditPort.refund(saved.getId(), "CANCEL_REFUND");

            Membership updated = membershipRepository.findById(saved.getId()).get();
            assertThat(updated.getCreditsRemaining()).isEqualTo(6);

            List<CreditTransaction> transactions = creditTransactionRepository.findAll();
            assertThat(transactions).hasSize(1);

            CreditTransaction txn = transactions.get(0);
            assertThat(txn.getDelta()).isEqualTo(1);
            assertThat(txn.getReason()).isEqualTo("CANCEL_REFUND");
            assertThat(txn.getBalanceAfter()).isEqualTo(6);
        }

        // Unlimited case
        {
            membershipRepository.deleteAll();
            creditTransactionRepository.deleteAll();

            Instant now = Instant.now(clock);
            Instant startsAt = now.minus(1, ChronoUnit.HOURS);
            Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

            Membership membership = new Membership(
                memberId, planIdUnlimited, "ACTIVE", true,
                null, null, startsAt, expiresAt
            );
            Membership saved = membershipRepository.save(membership);

            creditPort.refund(saved.getId(), "CANCEL_REFUND");

            Membership updated = membershipRepository.findById(saved.getId()).get();
            assertThat(updated.getCreditsRemaining()).isNull();

            List<CreditTransaction> transactions = creditTransactionRepository.findAll();
            assertThat(transactions).isEmpty();
        }
    }

    // AC-10: Guarded update deducting from zero credits returns MEMBERSHIP_NO_CREDITS, never 500
    @Test
    void test_ac10_guarded_update_zero_credits_returns_error() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 0, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        assertThatThrownBy(() -> creditPort.deduct(saved.getId(), "BOOKING"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.MEMBERSHIP_NO_CREDITS);

        Membership unchanged = membershipRepository.findById(saved.getId()).get();
        assertThat(unchanged.getCreditsRemaining()).isEqualTo(0);
    }

    // AC-11: Atomicity: forced failure after ledger insert but before commit leaves both unchanged
    @Test
    void test_ac11_atomicity_failure_after_ledger_insert() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 5, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        // Use a transaction that will fail after ledger write but before commit
        TransactionTemplate tt = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> {
            tt.execute(status -> {
                creditPort.deduct(saved.getId(), "BOOKING");
                // Force a failure after ledger write but before transaction commit
                throw new RuntimeException("Simulated failure after ledger write");
            });
        }).isInstanceOf(RuntimeException.class).hasMessage("Simulated failure after ledger write");

        // Verify both balance and ledger are unchanged
        Membership unchanged = membershipRepository.findById(saved.getId()).get();
        assertThat(unchanged.getCreditsRemaining()).isEqualTo(5);

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        assertThat(transactions).isEmpty();
    }

    // AC-12: Concurrency: two parallel deductions with exactly 1 credit → one success, one error
    @Test
    void test_ac12_concurrency_one_credit_two_threads() throws InterruptedException {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 1, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        AtomicReference<Exception> thread1Exception = new AtomicReference<>();
        AtomicReference<Exception> thread2Exception = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                TransactionTemplate tt = new TransactionTemplate(transactionManager);
                tt.execute(status -> {
                    creditPort.deduct(saved.getId(), "BOOKING");
                    return null;
                });
            } catch (Exception e) {
                thread1Exception.set(e);
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                TransactionTemplate tt = new TransactionTemplate(transactionManager);
                tt.execute(status -> {
                    creditPort.deduct(saved.getId(), "BOOKING");
                    return null;
                });
            } catch (Exception e) {
                thread2Exception.set(e);
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdownNow();

        // Verify one succeeded and one failed
        boolean hasError1 = thread1Exception.get() != null;
        boolean hasError2 = thread2Exception.get() != null;
        assertThat(hasError1 ^ hasError2).isTrue(); // XOR: exactly one has error

        if (hasError1) {
            assertThat(thread1Exception.get()).isInstanceOf(ApiException.class);
            assertThat(((ApiException) thread1Exception.get()).getCode()).isEqualTo(ErrorCode.MEMBERSHIP_NO_CREDITS);
        }
        if (hasError2) {
            assertThat(thread2Exception.get()).isInstanceOf(ApiException.class);
            assertThat(((ApiException) thread2Exception.get()).getCode()).isEqualTo(ErrorCode.MEMBERSHIP_NO_CREDITS);
        }

        // Final state: 0 credits, 1 ledger row
        Membership final_state = membershipRepository.findById(saved.getId()).get();
        assertThat(final_state.getCreditsRemaining()).isEqualTo(0);

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
    }

    // AC-13: Invariant: after 20 mixed operations, creditsInitial + SUM(delta) == creditsRemaining
    @Test
    void test_ac13_invariant_20_operations() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 10, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        int initial = saved.getCreditsInitial();

        // Perform 20 mixed operations
        for (int i = 0; i < 10; i++) {
            creditPort.deduct(saved.getId(), "BOOKING");
        }
        for (int i = 0; i < 5; i++) {
            creditPort.refund(saved.getId(), "CANCEL_REFUND");
        }
        for (int i = 0; i < 5; i++) {
            creditPort.deduct(saved.getId(), "SESSION_CANCELLED_REFUND");
        }

        Membership final_state = membershipRepository.findById(saved.getId()).get();
        List<CreditTransaction> transactions = creditTransactionRepository.findAll();

        int sumDelta = transactions.stream().mapToInt(CreditTransaction::getDelta).sum();
        assertThat(initial + sumDelta).isEqualTo(final_state.getCreditsRemaining());
    }

    // AC-14: balanceAfter on each row equals running balance at that point
    @Test
    void test_ac14_balanceAfter_accuracy() {
        Instant now = Instant.now(clock);
        Instant startsAt = now.minus(1, ChronoUnit.HOURS);
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Membership membership = new Membership(
            memberId, planId, "ACTIVE", false,
            10, 10, startsAt, expiresAt
        );
        Membership saved = membershipRepository.save(membership);

        creditPort.deduct(saved.getId(), "BOOKING");
        creditPort.deduct(saved.getId(), "BOOKING");
        creditPort.refund(saved.getId(), "CANCEL_REFUND");
        creditPort.deduct(saved.getId(), "BOOKING");

        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        assertThat(transactions).hasSize(4);

        int runningBalance = saved.getCreditsInitial();
        for (CreditTransaction txn : transactions) {
            runningBalance += txn.getDelta();
            assertThat(txn.getBalanceAfter()).isEqualTo(runningBalance);
        }
    }
}
