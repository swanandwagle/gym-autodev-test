package com.studio.booking.member.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({MemberStatusGateService.class, Clock.class})
@Testcontainers
class MemberStatusGateLockTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("jakarta.persistence.lock.timeout", () -> "3000");
    }

    @Autowired private MemberRepository memberRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private MemberStatusGateService gate;

    private UUID memberId;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
        Member member = new Member("test@example.com", "Test Member", null, "ACTIVE", fixedClock);
        memberRepository.save(member);
        entityManager.flush();
        memberId = member.getId();
    }

    @Test
    void test_ac2_loadForTransaction_acquires_pessimistic_lock() throws Exception {
        AtomicInteger secondTransactionAttempted = new AtomicInteger(0);
        AtomicInteger secondTransactionBlocked = new AtomicInteger(0);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch firstTransactionReleased = new CountDownLatch(1);

        UUID testMemberId = memberId;

        Thread firstTransaction = new Thread(() -> {
            entityManager.clear();
            try {
                Member member = gate.loadForTransaction(testMemberId);
                assertThat(member).isNotNull();
                assertThat(member.getStatus()).isEqualTo("ACTIVE");

                firstTransactionLocked.countDown();
                firstTransactionReleased.await();

                Thread.sleep(100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread secondTransaction = new Thread(() -> {
            try {
                firstTransactionLocked.await();
                secondTransactionAttempted.incrementAndGet();

                long startTime = System.currentTimeMillis();
                try {
                    Member member = gate.loadForTransaction(testMemberId);
                    long endTime = System.currentTimeMillis();

                    long blockDuration = endTime - startTime;
                    if (blockDuration >= 50) {
                        secondTransactionBlocked.incrementAndGet();
                    }
                } finally {
                    firstTransactionReleased.countDown();
                }
            } catch (Exception e) {
                firstTransactionReleased.countDown();
                throw new RuntimeException(e);
            }
        });

        firstTransaction.start();
        secondTransaction.start();

        firstTransaction.join();
        secondTransaction.join();

        assertThat(secondTransactionAttempted.get()).isEqualTo(1);
        assertThat(secondTransactionBlocked.get()).isEqualTo(1);
    }

    @Test
    @Transactional
    void test_ac7_lock_timeout_surfaces_as_409() {
        Member member = memberRepository.findById(memberId).orElseThrow();
        gate.requireActive(member);

        try {
            gate.loadForTransaction(memberId);
        } catch (ApiException ex) {
            if (ex.getErrorCode() == ErrorCode.CONCURRENT_MODIFICATION) {
                assertThat(ex.getHttpStatus().value()).isEqualTo(409);
            }
        }
    }
}
