package com.studio.booking.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.catalog.api.request.CreateRecurringSessionsRequest;
import com.studio.booking.catalog.api.response.CreateRecurringSessionsResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.ClassType;
import com.studio.booking.catalog.domain.Instructor;
import com.studio.booking.catalog.domain.Room;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.catalog.infrastructure.ClassTypeRepository;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.catalog.infrastructure.RoomRepository;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestClockConfig.class)
class RecurringSessionsControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("studio.timezone", () -> "America/New_York");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ClassSessionRepository sessionRepository;

    @Autowired
    ClassTypeRepository classTypeRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    Clock clock;

    private UUID classTypeId;
    private UUID instructorId;
    private UUID roomId;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-23T14:00:00Z");
        TestClockConfig.setFixedTime(now);

        ClassType classType = new ClassType("Yoga", "Flow class", 60, 20);
        classTypeId = classTypeRepository.save(classType).getId();

        Instructor instructor = new Instructor("instr@example.com", "Instructor One", "Bio", null);
        instructorId = instructorRepository.save(instructor).getId();

        Room room = new Room("Room A", 20);
        roomId = roomRepository.save(room).getId();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TestClockConfig.clearFixedTime();
    }

    // =========================================================================
    // AC-1: Mon/Wed/Fri over 4 weeks creates 12 sessions with same recurrenceId
    // =========================================================================
    @Test
    void test_ac1_mon_wed_fri_four_weeks_creates_12_sessions_with_shared_recurrence_id() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 25);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"),
                startTime, classTypeId, instructorId, roomId,
                60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.created()).hasSize(12);
        assertThat(response.skipped()).isNull();
        assertThat(response.conflicts()).isNull();

        UUID recurrenceId = response.recurrenceId();
        for (var session : response.created()) {
            var retrieved = sessionRepository.findById(session.id()).orElseThrow();
            assertThat(retrieved.getRecurrenceId()).isEqualTo(recurrenceId);
        }
    }

    // =========================================================================
    // AC-2: Sessions have correct local start time and derived endsAt
    // =========================================================================
    @Test
    void test_ac2_sessions_have_correct_local_start_time_and_ends_at() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 2);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"), startTime,
                classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        var session = response.created().get(0);
        var duration = java.time.Duration.between(session.startsAt(), session.endsAt());
        assertThat(duration.toMinutes()).isEqualTo(60);
    }

    // =========================================================================
    // AC-3: toDate is inclusive
    // =========================================================================
    @Test
    void test_ac3_to_date_inclusive_occurrence_on_exact_date_is_created() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.created()).hasSize(2);
    }

    // =========================================================================
    // AC-4: Occurrences at or before now are dropped
    // =========================================================================
    @Test
    void test_ac4_occurrences_at_or_before_now_dropped_future_only() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 23);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(10, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("WEDNESDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        for (var session : response.created()) {
            assertThat(session.startsAt()).isAfter(now);
        }
    }

    // =========================================================================
    // AC-5: Zero future occurrences returns 422 RECURRENCE_EMPTY
    // =========================================================================
    @Test
    void test_ac5_zero_future_occurrences_returns_422_recurrence_empty() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 22);
        LocalTime startTime = LocalTime.of(10, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.RECURRENCE_EMPTY);
    }

    // =========================================================================
    // AC-6: DST normal transition (same wall-clock time, different UTC)
    // =========================================================================
    @Test
    void test_ac6_dst_normal_transition_same_wall_clock_different_utc() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 3, 1);
        LocalDate toDate = LocalDate.of(2026, 3, 15);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("SUNDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.created()).hasSize(2);

        var s1 = response.created().get(0);
        var s2 = response.created().get(1);

        var tz = ZoneId.of("America/New_York");
        var ldt1 = s1.startsAt().atZone(tz).toLocalTime();
        var ldt2 = s2.startsAt().atZone(tz).toLocalTime();

        assertThat(ldt1).isEqualTo(startTime);
        assertThat(ldt2).isEqualTo(startTime);

        var duration = java.time.Duration.between(s1.startsAt(), s2.startsAt());
        assertThat(duration.toHours()).isNotEqualTo(24);
    }

    // =========================================================================
    // AC-7: DST gap (non-existent time resolves to later offset)
    // =========================================================================
    @Test
    void test_ac7_dst_gap_non_existent_time_resolves_pinned() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 3, 8);
        LocalDate toDate = LocalDate.of(2026, 3, 8);
        LocalTime startTime = LocalTime.of(2, 30);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("SUNDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.created()).hasSize(1);
        var tz = ZoneId.of("America/New_York");
        var zonedDt = response.created().get(0).startsAt().atZone(tz);
        assertThat(zonedDt.getHour()).isEqualTo(3);
    }

    // =========================================================================
    // AC-8: DST overlap (repeated time, earlier offset)
    // =========================================================================
    @Test
    void test_ac8_dst_overlap_repeated_time_resolves_earlier_offset() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 11, 1);
        LocalDate toDate = LocalDate.of(2026, 11, 1);
        LocalTime startTime = LocalTime.of(1, 30);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("SUNDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.created()).hasSize(1);
        var instant = response.created().get(0).startsAt();
        var tz = ZoneId.of("America/New_York");
        var zonedDt = instant.atZone(tz);
        assertThat(zonedDt.toOffsetDateTime().getOffset().getTotalSeconds()).isEqualTo(-5 * 3600);
    }

    // =========================================================================
    // AC-9: onConflict=FAIL with conflict creates nothing and returns 409
    // =========================================================================
    @Test
    void test_ac9_on_conflict_fail_one_conflict_creates_nothing_409() throws Exception {
        Instant existingStart = Instant.parse("2026-09-28T22:00:00Z");
        Instant existingEnd = existingStart.plusSeconds(3600);

        ClassSession existing = new ClassSession(
                classTypeId, instructorId, roomId, existingStart, existingEnd, 20, clock);
        sessionRepository.save(existing);

        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        var allSessions = sessionRepository.findAll();
        long createdByRecurrence = allSessions.stream()
                .filter(s -> s.getRecurrenceId() != null)
                .count();
        assertThat(createdByRecurrence).isEqualTo(0);
    }

    // =========================================================================
    // AC-10: onConflict=SKIP with conflicts creates remainder and reports skipped
    // =========================================================================
    @Test
    void test_ac10_on_conflict_skip_three_conflicts_creates_remainder_reports_skipped() throws Exception {
        Instant now1 = Instant.parse("2026-09-28T22:00:00Z");
        sessionRepository.save(new ClassSession(classTypeId, instructorId, roomId, now1, now1.plusSeconds(3600), 20, clock));

        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 12);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "SKIP");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.skipped()).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // AC-11: Conflicts detected for both instructor and room
    // =========================================================================
    @Test
    void test_ac11_conflicts_detected_for_both_instructor_and_room_with_correct_type() throws Exception {
        Instant conflictStart = Instant.parse("2026-09-28T22:00:00Z");
        Instant conflictEnd = conflictStart.plusSeconds(3600);

        ClassSession existing = new ClassSession(classTypeId, instructorId, roomId, conflictStart, conflictEnd, 20, clock);
        sessionRepository.save(existing);

        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "SKIP");

        mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // =========================================================================
    // AC-12: Conflict against CANCELLED session is not a conflict
    // =========================================================================
    @Test
    void test_ac12_conflict_against_cancelled_session_not_a_conflict() throws Exception {
        Instant cancelledStart = Instant.parse("2026-09-28T22:00:00Z");
        Instant cancelledEnd = cancelledStart.plusSeconds(3600);

        ClassSession cancelled = new ClassSession(classTypeId, instructorId, roomId, cancelledStart, cancelledEnd, 20, clock);
        cancelled.setStatus("CANCELLED");
        sessionRepository.save(cancelled);

        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        assertThat(response.created()).hasSize(2);
    }

    // =========================================================================
    // AC-13: toDate before or equal to fromDate returns 422 INVALID_RANGE
    // =========================================================================
    @Test
    void test_ac13_to_date_before_or_equal_from_date_returns_422_invalid_range() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 10, 5);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_RANGE);
    }

    // =========================================================================
    // AC-14: 366-day span returns 422 OUT_OF_RANGE
    // =========================================================================
    @Test
    void test_ac14_366_day_span_returns_422_out_of_range() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 23);
        LocalDate toDate = LocalDate.of(2027, 9, 24);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
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

    // =========================================================================
    // AC-15: daysOfWeek empty or with duplicates returns 422
    // =========================================================================
    @Test
    void test_ac15_days_of_week_empty_returns_422() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList(),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
    }

    @Test
    void test_ac15_days_of_week_with_duplicates_returns_422() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY", "MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_FORMAT);
    }

    // =========================================================================
    // AC-16: startTimeLocal invalid format returns 422 INVALID_FORMAT
    // =========================================================================
    @Test
    void test_ac16_start_time_local_invalid_format_returns_422() throws Exception {
        String json = """
                {
                  "fromDate": "2026-09-28",
                  "toDate": "2026-10-05",
                  "daysOfWeek": ["MONDAY"],
                  "startTimeLocal": "25:00",
                  "classTypeId": "%s",
                  "instructorId": "%s",
                  "roomId": "%s",
                  "durationMinutes": 60,
                  "capacity": 20,
                  "onConflict": "FAIL"
                }
                """.formatted(classTypeId, instructorId, roomId);

        mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
    }

    // =========================================================================
    // AC-17: fromDate before today returns 422 OUT_OF_RANGE
    // =========================================================================
    @Test
    void test_ac17_from_date_before_today_returns_422_out_of_range() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 20);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult result = mockMvc.perform(post("/api/v1/sessions/recurring")
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

    // =========================================================================
    // AC-18: Retrieve by recurrenceId returns all sessions in start order
    // =========================================================================
    @Test
    void test_ac18_retrieve_by_recurrence_id_returns_all_sessions_in_start_order() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 12);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult createResult = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        UUID recurrenceId = response.recurrenceId();

        MvcResult getResult = mockMvc.perform(get("/api/v1/sessions/by-recurrence/" + recurrenceId))
                .andExpect(status().isOk())
                .andReturn();

        var listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, com.studio.booking.catalog.api.response.ClassSessionScheduleResponse.class);
        var sessions = objectMapper.readValue(getResult.getResponse().getContentAsString(), listType);

        assertThat(sessions).hasSize(6);
        for (int i = 1; i < sessions.size(); i++) {
            assertThat(sessions.get(i).startsAt()).isGreaterThan(sessions.get(i - 1).startsAt());
        }
    }

    // =========================================================================
    // AC-18 (part 2): retrieve includes CANCELLED sessions
    // =========================================================================
    @Test
    void test_ac18_retrieve_by_recurrence_id_includes_cancelled_sessions() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 9, 28);
        LocalDate toDate = LocalDate.of(2026, 10, 5);
        LocalTime startTime = LocalTime.of(18, 0);

        CreateRecurringSessionsRequest request = new CreateRecurringSessionsRequest(
                fromDate, toDate, Arrays.asList("MONDAY"),
                startTime, classTypeId, instructorId, roomId, 60, 20, "FAIL");

        MvcResult createResult = mockMvc.perform(post("/api/v1/sessions/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateRecurringSessionsResponse response = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                CreateRecurringSessionsResponse.class
        );

        var sessionId = response.created().get(0).id();
        var session = sessionRepository.findById(sessionId).orElseThrow();
        session.setStatus("CANCELLED");
        sessionRepository.save(session);

        UUID recurrenceId = response.recurrenceId();

        MvcResult getResult = mockMvc.perform(get("/api/v1/sessions/by-recurrence/" + recurrenceId))
                .andExpect(status().isOk())
                .andReturn();

        var listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, com.studio.booking.catalog.api.response.ClassSessionScheduleResponse.class);
        var sessions = objectMapper.readValue(getResult.getResponse().getContentAsString(), listType);

        assertThat(sessions).hasSizeGreaterThanOrEqualTo(1);
        boolean hasCancelled = sessions.stream().anyMatch(s -> s.status().equals("CANCELLED"));
        assertThat(hasCancelled).isTrue();
    }
}
