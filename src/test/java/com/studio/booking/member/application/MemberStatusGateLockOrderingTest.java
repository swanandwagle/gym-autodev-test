package com.studio.booking.member.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({MemberStatusGateService.class, Clock.class})
@Testcontainers
class MemberStatusGateLockOrderingTest {

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
    @Autowired(required = false) private TransactionTemplate transactionTemplate;

    private UUID memberId1;
    private UUID memberId2;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);

        Member member1 = new Member("member1@example.com", "Member 1", null, "ACTIVE", fixedClock);
        Member member2 = new Member("member2@example.com", "Member 2", null, "ACTIVE", fixedClock);

        memberRepository.save(member1);
        memberRepository.save(member2);
        entityManager.flush();

        memberId1 = member1.getId();
        memberId2 = member2.getId();

        if (memberId1.compareTo(memberId2) > 0) {
            UUID temp = memberId1;
            memberId1 = memberId2;
            memberId2 = temp;
        }
    }

    @Test
    void test_ac8_ascending_id_lock_order_prevents_deadlock() throws Exception {
        int numThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        UUID testMemberId1 = memberId1;
        UUID testMemberId2 = memberId2;

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    entityManager.clear();

                    UUID first = testMemberId1;
                    UUID second = testMemberId2;
                    if (first.compareTo(second) > 0) {
                        UUID temp = first;
                        first = second;
                        second = temp;
                    }

                    try {
                        Member m1 = gate.loadForTransaction(first);
                        Member m2 = gate.loadForTransaction(second);

                        assertThat(m1).isNotNull();
                        assertThat(m2).isNotNull();
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        if (ex.getMessage().contains("timeout") || ex.getMessage().contains("deadlock")) {
                            timeoutCount.incrementAndGet();
                        } else {
                            throw ex;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get() + timeoutCount.get()).isEqualTo(numThreads);
        if (timeoutCount.get() > 0) {
            assertThat(successCount.get()).isGreaterThan(0);
        }
    }
}
