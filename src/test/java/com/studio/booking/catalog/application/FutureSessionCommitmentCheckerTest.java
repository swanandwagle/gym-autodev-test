package com.studio.booking.catalog.application;

import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.ClassType;
import com.studio.booking.catalog.domain.Instructor;
import com.studio.booking.catalog.domain.Room;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.catalog.infrastructure.ClassTypeRepository;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.catalog.infrastructure.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestClockConfig.class)
class FutureSessionCommitmentCheckerTest {

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
    FutureSessionCommitmentChecker checker;

    @Autowired
    ClassSessionRepository classSessionRepository;

    @Autowired
    ClassTypeRepository classTypeRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    RoomRepository roomRepository;

    private UUID instructorId;
    private UUID roomId;
    private UUID classTypeId;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-23T10:00:00Z");
        TestClockConfig.setFixedTime(now);

        ClassType classType = new ClassType("Yoga");
        classTypeId = classTypeRepository.save(classType).getId();

        Instructor instructor = new Instructor("instr@example.com", "Instructor One", "Bio", null);
        instructorId = instructorRepository.save(instructor).getId();

        Room room = new Room("Room A", 20);
        roomId = roomRepository.save(room).getId();
    }

    @AfterEach
    void tearDown() {
        TestClockConfig.clearFixedTime();
    }

    // =========================================================================
    // AC-1: forInstructor returns count 0 for an instructor with no sessions
    // =========================================================================
    @Test
    void test_ac1_forInstructor_returns_count_zero_when_no_sessions() {
        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    // =========================================================================
    // AC-2: Returns count 0 when the instructor's only future session is CANCELLED
    // =========================================================================
    @Test
    void test_ac2_forInstructor_ignores_cancelled_sessions() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.plusSeconds(3600), now.plusSeconds(5400), 20);
        session.setStatus("CANCELLED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    // =========================================================================
    // AC-3: Returns count 0 when the instructor's only sessions are in the past
    // =========================================================================
    @Test
    void test_ac3_forInstructor_ignores_past_sessions() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.minusSeconds(3600), now.minusSeconds(1800), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    // =========================================================================
    // AC-4: Returns count 0 when the instructor's only sessions are COMPLETED
    // =========================================================================
    @Test
    void test_ac4_forInstructor_ignores_completed_sessions() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.plusSeconds(3600), now.plusSeconds(5400), 20);
        session.setStatus("COMPLETED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    // =========================================================================
    // AC-5: Returns the correct count and earliest start when future SCHEDULED sessions exist
    // =========================================================================
    @Test
    void test_ac5_forInstructor_counts_scheduled_sessions() {
        Instant start1 = now.plusSeconds(3600);
        ClassSession session1 = new ClassSession(classTypeId, instructorId, roomId,
                start1, start1.plusSeconds(1800), 20);
        session1.setStatus("SCHEDULED");
        classSessionRepository.save(session1);

        Instant start2 = now.plusSeconds(7200);
        ClassSession session2 = new ClassSession(classTypeId, instructorId, roomId,
                start2, start2.plusSeconds(1800), 20);
        session2.setStatus("SCHEDULED");
        classSessionRepository.save(session2);

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isEqualTo(2);
        assertThat(result.earliestStart()).isEqualTo(start1);
        assertThat(result.sessionIds()).hasSize(2);
        assertThat(result.firstSessionCapacity()).isEqualTo(20);
    }

    // =========================================================================
    // AC-6: Boundary: session starting exactly at now does NOT count;
    //       one starting one second later DOES count
    // =========================================================================
    @Test
    void test_ac6_boundary_exact_instant_not_counted() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now, now.plusSeconds(1800), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac6_boundary_one_second_later_counted() {
        Instant start = now.plusSeconds(1);
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                start, start.plusSeconds(1800), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.earliestStart()).isEqualTo(start);
        assertThat(result.sessionIds()).hasSize(1);
        assertThat(result.firstSessionCapacity()).isEqualTo(20);
    }

    // =========================================================================
    // AC-7: forRoom behaves identically to forInstructor for all cases
    // =========================================================================
    @Test
    void test_ac7_forRoom_no_sessions() {
        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac7_forRoom_ignores_cancelled_sessions() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.plusSeconds(3600), now.plusSeconds(5400), 20);
        session.setStatus("CANCELLED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac7_forRoom_ignores_past_sessions() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.minusSeconds(3600), now.minusSeconds(1800), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac7_forRoom_ignores_completed_sessions() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.plusSeconds(3600), now.plusSeconds(5400), 20);
        session.setStatus("COMPLETED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac7_forRoom_counts_scheduled_sessions() {
        Instant start1 = now.plusSeconds(3600);
        ClassSession session1 = new ClassSession(classTypeId, instructorId, roomId,
                start1, start1.plusSeconds(1800), 20);
        session1.setStatus("SCHEDULED");
        classSessionRepository.save(session1);

        Instant start2 = now.plusSeconds(7200);
        ClassSession session2 = new ClassSession(classTypeId, instructorId, roomId,
                start2, start2.plusSeconds(1800), 20);
        session2.setStatus("SCHEDULED");
        classSessionRepository.save(session2);

        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isEqualTo(2);
        assertThat(result.earliestStart()).isEqualTo(start1);
        assertThat(result.sessionIds()).hasSize(2);
        assertThat(result.firstSessionCapacity()).isEqualTo(20);
    }

    @Test
    void test_ac7_forRoom_boundary_exact_instant_not_counted() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now, now.plusSeconds(1800), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac7_forRoom_boundary_one_second_later_counted() {
        Instant start = now.plusSeconds(1);
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                start, start.plusSeconds(1800), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoom(roomId);

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.earliestStart()).isEqualTo(start);
        assertThat(result.sessionIds()).hasSize(1);
        assertThat(result.firstSessionCapacity()).isEqualTo(20);
    }

    // =========================================================================
    // AC-8: forRoomExceedingCapacity returns only sessions whose capacity is strictly above proposed value
    // =========================================================================
    @Test
    void test_ac8_forRoomExceedingCapacity_counts_only_exceeding() {
        Instant start1 = now.plusSeconds(3600);
        ClassSession session1 = new ClassSession(classTypeId, instructorId, roomId,
                start1, start1.plusSeconds(1800), 25);
        session1.setStatus("SCHEDULED");
        classSessionRepository.save(session1);

        Instant start2 = now.plusSeconds(7200);
        ClassSession session2 = new ClassSession(classTypeId, instructorId, roomId,
                start2, start2.plusSeconds(1800), 20);
        session2.setStatus("SCHEDULED");
        classSessionRepository.save(session2);

        Instant start3 = now.plusSeconds(10800);
        ClassSession session3 = new ClassSession(classTypeId, instructorId, roomId,
                start3, start3.plusSeconds(1800), 15);
        session3.setStatus("SCHEDULED");
        classSessionRepository.save(session3);

        FutureCommitmentResult result = checker.forRoomExceedingCapacity(roomId, 20);

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.earliestStart()).isEqualTo(start1);
        assertThat(result.sessionIds()).containsExactly(session1.getId());
        assertThat(result.firstSessionCapacity()).isEqualTo(25);
    }

    // =========================================================================
    // AC-9: forRoomExceedingCapacity ignores CANCELLED and past sessions
    // =========================================================================
    @Test
    void test_ac9_forRoomExceedingCapacity_ignores_cancelled() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.plusSeconds(3600), now.plusSeconds(5400), 25);
        session.setStatus("CANCELLED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoomExceedingCapacity(roomId, 20);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    @Test
    void test_ac9_forRoomExceedingCapacity_ignores_past() {
        ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                now.minusSeconds(3600), now.minusSeconds(1800), 25);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        FutureCommitmentResult result = checker.forRoomExceedingCapacity(roomId, 20);

        assertThat(result.count()).isZero();
        assertThat(result.earliestStart()).isNull();
        assertThat(result.sessionIds()).isEmpty();
        assertThat(result.firstSessionCapacity()).isNull();
    }

    // =========================================================================
    // AC-10: sessionIds capped at 10; count reports full total with 15 conflicting sessions
    // =========================================================================
    @Test
    void test_ac10_sessionIds_capped_at_10() {
        for (int i = 0; i < 15; i++) {
            Instant start = now.plusSeconds(3600 + (i * 100));
            ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                    start, start.plusSeconds(1800), 20);
            session.setStatus("SCHEDULED");
            classSessionRepository.save(session);
        }

        FutureCommitmentResult result = checker.forInstructor(instructorId);

        assertThat(result.count()).isEqualTo(15);
        assertThat(result.sessionIds()).hasSize(10);
        assertThat(result.firstSessionCapacity()).isEqualTo(20);
    }

    // =========================================================================
    // AC-11: Single query per call (no N+1)
    // =========================================================================
    @Test
    void test_ac11_single_query_per_call_forInstructor() {
        for (int i = 0; i < 5; i++) {
            Instant start = now.plusSeconds(3600 + (i * 100));
            ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                    start, start.plusSeconds(1800), 20);
            session.setStatus("SCHEDULED");
            classSessionRepository.save(session);
        }

        checker.forInstructor(instructorId);
    }

    @Test
    void test_ac11_single_query_per_call_forRoom() {
        for (int i = 0; i < 5; i++) {
            Instant start = now.plusSeconds(3600 + (i * 100));
            ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                    start, start.plusSeconds(1800), 20);
            session.setStatus("SCHEDULED");
            classSessionRepository.save(session);
        }

        checker.forRoom(roomId);
    }

    @Test
    void test_ac11_single_query_per_call_forRoomExceedingCapacity() {
        for (int i = 0; i < 5; i++) {
            Instant start = now.plusSeconds(3600 + (i * 100));
            ClassSession session = new ClassSession(classTypeId, instructorId, roomId,
                    start, start.plusSeconds(1800), 20);
            session.setStatus("SCHEDULED");
            classSessionRepository.save(session);
        }

        checker.forRoomExceedingCapacity(roomId, 15);
    }
}
