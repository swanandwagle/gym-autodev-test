package com.studio.booking.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.catalog.api.request.CreateClassSessionRequest;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
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
import org.springframework.context.annotation.Import;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestClockConfig.class)
class ClassSessionControllerIntegrationTest {

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
        now = Instant.parse("2026-09-23T10:00:00Z");
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
    // AC-1: A valid request creates a SCHEDULED session with bookedCount: 0
    //       and the Location header
    // =========================================================================
    @Test
    void test_ac1_valid_request_creates_scheduled_session_with_booked_count_zero_and_location_header() throws Exception {
        Instant startsAt = now.plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsAt, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.bookedCount()).isEqualTo(0);
        assertThat(response.startsAt()).isEqualTo(startsAt);
        assertThat(response.endsAt()).isEqualTo(endsAt);
        assertThat(result.getResponse().getHeader("Location")).isNotNull();
        assertThat(result.getResponse().getHeader("Location")).contains("/api/v1/sessions/" + response.id());
    }

    // =========================================================================
    // AC-2: Omitting durationMinutes and capacity takes both from the class type
    // =========================================================================
    @Test
    void test_ac2_omitting_duration_and_capacity_uses_class_type_defaults() throws Exception {
        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsAt, null, null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        assertThat(response.capacity()).isEqualTo(20);
        assertThat(response.endsAt()).isEqualTo(startsAt.plusSeconds(3600));
    }

    // =========================================================================
    // AC-3: Supplying durationMinutes and capacity overrides the class type defaults
    // =========================================================================
    @Test
    void test_ac3_supplying_duration_and_capacity_overrides_class_type() throws Exception {
        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsAt, 90, 15
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        assertThat(response.capacity()).isEqualTo(15);
        assertThat(response.endsAt()).isEqualTo(startsAt.plusSeconds(5400));
    }

    // =========================================================================
    // AC-4: endsAt equals startsAt plus the effective duration
    // =========================================================================
    @Test
    void test_ac4_ends_at_equals_starts_at_plus_duration() throws Exception {
        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsAt, 45, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        Instant expectedEndsAt = startsAt.plusSeconds(45 * 60);
        assertThat(response.endsAt()).isEqualTo(expectedEndsAt);
    }

    // =========================================================================
    // AC-5: A startsAt in the past returns 422 FUTURE_REQUIRED
    // =========================================================================
    @Test
    void test_ac5_past_start_time_returns_422_future_required() throws Exception {
        Instant pastTime = now.minusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                pastTime, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.FUTURE_REQUIRED);
    }

    // =========================================================================
    // AC-6: A startsAt with non-zero seconds returns 422 INVALID_FORMAT
    // =========================================================================
    @Test
    void test_ac6_non_zero_seconds_returns_422_invalid_format() throws Exception {
        Instant startsAt = now.plusSeconds(3600);
        Instant startsWithSeconds = Instant.ofEpochSecond(startsAt.getEpochSecond() + 30);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsWithSeconds, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.INVALID_FORMAT);
    }

    // =========================================================================
    // AC-7: A startsAt supplied with a non-UTC offset is accepted and returned as UTC
    // =========================================================================
    @Test
    void test_ac7_non_utc_offset_accepted_and_returned_as_utc() throws Exception {
        Instant futureTime = now.plusSeconds(3600);
        OffsetDateTime offsetTime = OffsetDateTime.ofInstant(futureTime, ZoneOffset.ofHours(5, 30));

        String json = """
                {
                  "classTypeId": "%s",
                  "instructorId": "%s",
                  "roomId": "%s",
                  "startsAt": "%s",
                  "durationMinutes": 60,
                  "capacity": 20
                }
                """.formatted(classTypeId, instructorId, roomId, offsetTime.toString());

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        assertThat(response.startsAt()).isEqualTo(futureTime);
    }

    // =========================================================================
    // AC-8: capacity above the room's capacity returns 409 SESSION_CAPACITY_EXCEEDS_ROOM
    // =========================================================================
    @Test
    void test_ac8_capacity_exceeds_room_returns_409_session_capacity_exceeds_room() throws Exception {
        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsAt, 60, 25
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.SESSION_CAPACITY_EXCEEDS_ROOM);
    }

    // =========================================================================
    // AC-9: An inactive class type, instructor, or room each return their specific 409
    // =========================================================================
    @Test
    void test_ac9_inactive_class_type_returns_409() throws Exception {
        ClassType inactiveType = new ClassType("Inactive Type", "Desc", 60, 20);
        inactiveType.deactivate();
        UUID inactiveTypeId = classTypeRepository.save(inactiveType).getId();

        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                inactiveTypeId, instructorId, roomId,
                startsAt, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.CLASS_TYPE_INACTIVE);
    }

    @Test
    void test_ac9_inactive_instructor_returns_409() throws Exception {
        Instructor inactiveInstructor = new Instructor("inactive@example.com", "Inactive", "Bio", null);
        inactiveInstructor.deactivate();
        UUID inactiveInstructorId = instructorRepository.save(inactiveInstructor).getId();

        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, inactiveInstructorId, roomId,
                startsAt, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.INSTRUCTOR_INACTIVE);
    }

    @Test
    void test_ac9_inactive_room_returns_409() throws Exception {
        Room inactiveRoom = new Room("Inactive Room", 20);
        inactiveRoom.deactivate();
        UUID inactiveRoomId = roomRepository.save(inactiveRoom).getId();

        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, inactiveRoomId,
                startsAt, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.ROOM_INACTIVE);
    }

    // =========================================================================
    // AC-10: Instructor conflict: overlapping sessions return 409 with conflicting session id
    // =========================================================================
    @Test
    void test_ac10_instructor_conflict_overlapping_returns_409_with_conflicting_session_id() throws Exception {
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession existing = new ClassSession(classTypeId, instructorId, roomId,
                session1Start, session1End, 20);
        ClassSession saved = sessionRepository.save(existing);

        Instant session2Start = session1Start.plusSeconds(1800);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session2Start, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.SESSION_INSTRUCTOR_CONFLICT);
        assertThat(env.detail()).contains(saved.getId().toString());
    }

    // =========================================================================
    // AC-11: Room conflict: overlapping sessions return 409 with conflicting session id
    // =========================================================================
    @Test
    void test_ac11_room_conflict_overlapping_returns_409_with_conflicting_session_id() throws Exception {
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        Instructor instructor2 = new Instructor("instr2@example.com", "Instructor Two", "Bio", null);
        UUID instructorId2 = instructorRepository.save(instructor2).getId();

        ClassSession existing = new ClassSession(classTypeId, instructorId2, roomId,
                session1Start, session1End, 20);
        ClassSession saved = sessionRepository.save(existing);

        Instant session2Start = session1Start.plusSeconds(1800);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session2Start, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.SESSION_ROOM_CONFLICT);
        assertThat(env.detail()).contains(saved.getId().toString());
    }

    // =========================================================================
    // AC-12: Half-open boundary: 10:00-11:00 and 11:00-12:00 are both accepted
    // =========================================================================
    @Test
    void test_ac12_half_open_boundary_10_11_and_11_12_both_accepted() throws Exception {
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        CreateClassSessionRequest request1 = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session1Start, 60, 20
        );

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        Instant session2Start = session1End;
        CreateClassSessionRequest request2 = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session2Start, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );
        assertThat(response.startsAt()).isEqualTo(session2Start);
    }

    // =========================================================================
    // AC-13: Half-open boundary: 10:00-11:00 and 10:59-11:59 conflict
    // =========================================================================
    @Test
    void test_ac13_half_open_boundary_10_11_and_1059_1159_conflict() throws Exception {
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        CreateClassSessionRequest request1 = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session1Start, 60, 20
        );

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        Instant session2Start = session1Start.plusSeconds(3540);
        CreateClassSessionRequest request2 = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session2Start, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.SESSION_INSTRUCTOR_CONFLICT);
    }

    // =========================================================================
    // AC-14: Cancelled slot reuse: cancel a session, then create a new one
    //        in the identical slot with the same instructor and room — succeeds
    // =========================================================================
    @Test
    void test_ac14_cancel_then_reuse_same_slot_succeeds() throws Exception {
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession existing = new ClassSession(classTypeId, instructorId, roomId,
                session1Start, session1End, 20);
        ClassSession saved = sessionRepository.save(existing);

        saved.setStatus("CANCELLED");
        sessionRepository.save(saved);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                session1Start, 60, 20
        );

        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );
        assertThat(response.status()).isEqualTo("SCHEDULED");
    }

    // =========================================================================
    // AC-15 & AC-16: Concurrency: two parallel requests for overlapping sessions
    //                One succeeds; loser gets 409 with same code as pre-check path
    // =========================================================================
    @Test
    void test_ac15_concurrency_two_parallel_overlapping_requests_one_succeeds_one_409() throws Exception {
        Instant sessionStart = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                sessionStart, 60, 20
        );

        AtomicReference<Integer> status1 = new AtomicReference<>();
        AtomicReference<Integer> status2 = new AtomicReference<>();
        AtomicReference<ErrorCode> errorCode1 = new AtomicReference<>();
        AtomicReference<ErrorCode> errorCode2 = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                status1.set(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 409) {
                    ErrorEnvelope env = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ErrorEnvelope.class
                    );
                    errorCode1.set(env.code());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                status2.set(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 409) {
                    ErrorEnvelope env = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            ErrorEnvelope.class
                    );
                    errorCode2.set(env.code());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        assertThat(status1.get()).isIn(201, 409);
        assertThat(status2.get()).isIn(201, 409);
        assertThat(status1.get() + status2.get()).isEqualTo(610);

        if (status1.get() == 409) {
            assertThat(errorCode1.get()).isEqualTo(ErrorCode.SESSION_INSTRUCTOR_CONFLICT);
        }
        if (status2.get() == 409) {
            assertThat(errorCode2.get()).isEqualTo(ErrorCode.SESSION_INSTRUCTOR_CONFLICT);
        }
    }

    // =========================================================================
    // AC-17: GET returns the session; unknown id returns 404; malformed UUID returns 422
    // =========================================================================
    @Test
    void test_ac17_get_returns_session() throws Exception {
        Instant startsAt = now.plusSeconds(3600);

        CreateClassSessionRequest request = new CreateClassSessionRequest(
                classTypeId, instructorId, roomId,
                startsAt, 60, 20
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ClassSessionScheduleResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        MvcResult getResult = mockMvc.perform(get("/api/v1/sessions/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        ClassSessionScheduleResponse retrieved = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                ClassSessionScheduleResponse.class
        );

        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.startsAt()).isEqualTo(startsAt);
    }

    @Test
    void test_ac17_get_unknown_id_returns_404() throws Exception {
        UUID unknownId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/api/v1/sessions/" + unknownId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void test_ac17_get_malformed_uuid_returns_422() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sessions/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
