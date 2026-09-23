package com.studio.booking.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.booking.domain.WaitlistEntry;
import com.studio.booking.booking.infrastructure.WaitlistEntryRepository;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.Instructor;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.web.PageResponse;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InstructorScheduleControllerIntegrationTest {

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
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Clock clock;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    ClassSessionRepository classSessionRepository;

    @Autowired
    WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    EntityManager entityManager;

    private UUID instructorId;
    private UUID classTypeId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
        classTypeId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        Instructor instructor = new Instructor("schedule@example.com", "Schedule Instructor", "Bio", List.of());
        instructorRepository.save(instructor);
        instructorId = instructor.getId();
    }

    // =========================================================================
    // AC-1: Default window returns SCHEDULED sessions for next 7 days
    // =========================================================================

    @Test
    void ac1_defaultWindowReturnsScheduledSessionsForNext7Days() throws Exception {
        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);
        Instant in2Days = now.plusSeconds(2 * 24 * 3600);
        Instant in10Days = now.plusSeconds(10 * 24 * 3600);

        ClassSession session1 = new ClassSession(classTypeId, instructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        session1.setStatus("SCHEDULED");
        classSessionRepository.save(session1);

        ClassSession session2 = new ClassSession(classTypeId, instructorId, roomId, in2Days, in2Days.plusSeconds(3600), 20);
        session2.setStatus("SCHEDULED");
        classSessionRepository.save(session2);

        ClassSession session3 = new ClassSession(classTypeId, instructorId, roomId, in10Days, in10Days.plusSeconds(3600), 20);
        session3.setStatus("SCHEDULED");
        classSessionRepository.save(session3);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(2);
    }

    // =========================================================================
    // AC-2: Only that instructor's sessions are returned
    // =========================================================================

    @Test
    void ac2_onlyInstructorSessionsReturned() throws Exception {
        UUID otherInstructorId = UUID.randomUUID();
        Instructor otherInstructor = new Instructor("other@example.com", "Other Instructor", "Bio", List.of());
        instructorRepository.save(otherInstructor);
        otherInstructorId = otherInstructor.getId();

        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);

        ClassSession mySession = new ClassSession(classTypeId, instructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        mySession.setStatus("SCHEDULED");
        classSessionRepository.save(mySession);

        ClassSession otherSession = new ClassSession(classTypeId, otherInstructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        otherSession.setStatus("SCHEDULED");
        classSessionRepository.save(otherSession);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(1);
        List<ClassSessionScheduleResponse> content = (List<ClassSessionScheduleResponse>) (List<?>) page.content();
        assertThat(content.get(0).instructorId()).isEqualTo(instructorId);
    }

    // =========================================================================
    // AC-3a: status=CANCELLED returns only cancelled sessions
    // =========================================================================

    @Test
    void ac3a_statusCancelledReturnsOnlyCancelledSessions() throws Exception {
        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);

        ClassSession scheduled = new ClassSession(classTypeId, instructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        scheduled.setStatus("SCHEDULED");
        classSessionRepository.save(scheduled);

        ClassSession cancelled = new ClassSession(classTypeId, instructorId, roomId, in1Hour.plusSeconds(7200), in1Hour.plusSeconds(10800), 20);
        cancelled.setStatus("CANCELLED");
        classSessionRepository.save(cancelled);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule?status=CANCELLED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(1);
        List<ClassSessionScheduleResponse> content = (List<ClassSessionScheduleResponse>) (List<?>) page.content();
        assertThat(content.get(0).status()).isEqualTo("CANCELLED");
    }

    // =========================================================================
    // AC-3b: status=SCHEDULED,COMPLETED returns both
    // =========================================================================

    @Test
    void ac3b_statusScheduledCompletedReturnsBoth() throws Exception {
        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);

        ClassSession scheduled = new ClassSession(classTypeId, instructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        scheduled.setStatus("SCHEDULED");
        classSessionRepository.save(scheduled);

        ClassSession completed = new ClassSession(classTypeId, instructorId, roomId, in1Hour.plusSeconds(7200), in1Hour.plusSeconds(10800), 20);
        completed.setStatus("COMPLETED");
        classSessionRepository.save(completed);

        ClassSession cancelled = new ClassSession(classTypeId, instructorId, roomId, in1Hour.plusSeconds(14400), in1Hour.plusSeconds(18000), 20);
        cancelled.setStatus("CANCELLED");
        classSessionRepository.save(cancelled);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule?status=SCHEDULED,COMPLETED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(2);
    }

    // =========================================================================
    // AC-4a: A session starting exactly at from is included
    // =========================================================================

    @Test
    void ac4a_sessionStartingAtFromIncluded() throws Exception {
        LocalDate fromDate = LocalDate.now(clock);
        LocalDate toDate = fromDate.plusDays(7);
        Instant fromInstant = fromDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant toInstant = toDate.atStartOfDay(ZoneId.of("UTC")).toInstant();

        ClassSession session = new ClassSession(classTypeId, instructorId, roomId, fromInstant, fromInstant.plusSeconds(3600), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .param("from", fromDate.toString())
                        .param("to", toDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(1);
    }

    // =========================================================================
    // AC-4b: A session starting exactly at to is excluded (half-open boundary)
    // =========================================================================

    @Test
    void ac4b_sessionStartingAtToExcluded() throws Exception {
        LocalDate fromDate = LocalDate.now(clock);
        LocalDate toDate = fromDate.plusDays(7);
        Instant fromInstant = fromDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant toInstant = toDate.atStartOfDay(ZoneId.of("UTC")).toInstant();

        ClassSession sessionAtTo = new ClassSession(classTypeId, instructorId, roomId, toInstant, toInstant.plusSeconds(3600), 20);
        sessionAtTo.setStatus("SCHEDULED");
        classSessionRepository.save(sessionAtTo);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .param("from", fromDate.toString())
                        .param("to", toDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).isEmpty();
    }

    // =========================================================================
    // AC-5: to before or equal to from returns 422 INVALID_RANGE
    // =========================================================================

    @Test
    void ac5_toBeforeOrEqualFromReturns422InvalidRange() throws Exception {
        LocalDate fromDate = LocalDate.now(clock);
        LocalDate toDate = fromDate;

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .param("from", fromDate.toString())
                        .param("to", toDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-6: A 93-day span returns 422 OUT_OF_RANGE
    // =========================================================================

    @Test
    void ac6_93DaySpanReturns422OutOfRange() throws Exception {
        LocalDate fromDate = LocalDate.now(clock);
        LocalDate toDate = fromDate.plusDays(93);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .param("from", fromDate.toString())
                        .param("to", toDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-7: Unknown instructorId returns 404 INSTRUCTOR_NOT_FOUND
    // =========================================================================

    @Test
    void ac7_unknownInstructorIdReturns404InstructorNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + unknownId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.INSTRUCTOR_NOT_FOUND);
    }

    // =========================================================================
    // AC-8: Malformed instructorId returns 422 INVALID_FORMAT
    // =========================================================================

    @Test
    void ac8_malformedInstructorIdReturns422InvalidFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/instructors/not-a-uuid/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-9: No sessions in window returns 200 with empty content array
    // =========================================================================

    @Test
    void ac9_noSessionsInWindowReturns200EmptyContent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).isEmpty();
    }

    // =========================================================================
    // AC-10: Supplying sort returns 422
    // =========================================================================

    @Test
    void ac10_supplySortReturns422() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .param("sort", "startsAt,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-11: availableSpots equals capacity - bookedCount
    // =========================================================================

    @Test
    void ac11_availableSpotsEqualsCapacityMinusBookedCount() throws Exception {
        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);

        ClassSession session = new ClassSession(classTypeId, instructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        session.setBookedCount(7);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        List<ClassSessionScheduleResponse> content = (List<ClassSessionScheduleResponse>) (List<?>) page.content();
        assertThat(content.get(0).availableSpots()).isEqualTo(13);
        assertThat(content.get(0).bookedCount()).isEqualTo(7);
        assertThat(content.get(0).capacity()).isEqualTo(20);
    }

    // =========================================================================
    // AC-12: waitlistCount is correct
    // =========================================================================

    @Test
    void ac12_waitlistCountCorrect() throws Exception {
        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);

        ClassSession session = new ClassSession(classTypeId, instructorId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        session.setStatus("SCHEDULED");
        ClassSession saved = classSessionRepository.save(session);

        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();
        UUID member3 = UUID.randomUUID();

        WaitlistEntry entry1 = new WaitlistEntry(saved.getId(), member1, 1);
        WaitlistEntry entry2 = new WaitlistEntry(saved.getId(), member2, 2);
        WaitlistEntry entry3 = new WaitlistEntry(saved.getId(), member3, 3);
        waitlistEntryRepository.saveAll(List.of(entry1, entry2, entry3));

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        List<ClassSessionScheduleResponse> content = (List<ClassSessionScheduleResponse>) (List<?>) page.content();
        assertThat(content.get(0).waitlistCount()).isEqualTo(3);
    }

    // =========================================================================
    // AC-13: Bounded query count (N+1 avoidance)
    // =========================================================================

    @Test
    void ac13_boundedQueryCount() throws Exception {
        Instant now = clock.instant();

        for (int i = 0; i < 20; i++) {
            Instant sessionStart = now.plusSeconds((i + 1) * 3600);
            ClassSession session = new ClassSession(classTypeId, instructorId, roomId, sessionStart, sessionStart.plusSeconds(3600), 20);
            session.setStatus("SCHEDULED");
            classSessionRepository.save(session);
        }

        Session hibernateSession = entityManager.unwrap(Session.class);
        Statistics stats = hibernateSession.getSessionFactory().getStatistics();
        stats.clear();

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + instructorId + "/schedule?from=" + LocalDate.now(clock) + "&to=" + LocalDate.now(clock).plusDays(30))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(20);

        long queryCount = stats.getPreparedStatementCount();
        assertThat(queryCount).isLessThanOrEqualTo(10);
    }

    // =========================================================================
    // AC-14: Inactive instructor's schedule is still returned
    // =========================================================================

    @Test
    void ac14_inactiveInstructorScheduleStillReturned() throws Exception {
        Instructor inactiveInstructor = new Instructor("inactive@example.com", "Inactive Instructor", "Bio", List.of());
        instructorRepository.save(inactiveInstructor);
        UUID inactiveId = inactiveInstructor.getId();

        Instant now = clock.instant();
        Instant in1Hour = now.plusSeconds(3600);

        ClassSession session = new ClassSession(classTypeId, inactiveId, roomId, in1Hour, in1Hour.plusSeconds(3600), 20);
        session.setStatus("SCHEDULED");
        classSessionRepository.save(session);

        inactiveInstructor.deactivate();
        instructorRepository.save(inactiveInstructor);

        MvcResult result = mockMvc.perform(get("/api/v1/instructors/" + inactiveId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        assertThat(page.content()).hasSize(1);
    }
}
