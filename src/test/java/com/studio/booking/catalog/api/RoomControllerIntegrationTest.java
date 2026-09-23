package com.studio.booking.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.catalog.api.response.RoomResponse;
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
import com.studio.booking.shared.web.PageResponse;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoomControllerIntegrationTest {

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
    RoomRepository roomRepository;

    @Autowired
    ClassSessionRepository classSessionRepository;

    @Autowired
    ClassTypeRepository classTypeRepository;

    @Autowired
    InstructorRepository instructorRepository;

    private UUID roomId;
    private Instant baseTime;

    @BeforeEach
    void setUp() throws Exception {
        baseTime = clock.instant();

        String body = objectMapper.writeValueAsString(new CreateRoomRequestDto("Base Room", 20));
        MvcResult result = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        RoomResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RoomResponse.class
        );
        roomId = response.id();
    }

    // =========================================================================
    // AC-1: Create returns 201 with active: true
    // =========================================================================

    @Test
    void ac1_createReturns201WithActiveTrue() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateRoomRequestDto("New Room", 30));

        MvcResult result = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        RoomResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RoomResponse.class
        );

        assertThat(response.active()).isTrue();
        assertThat(response.version()).isEqualTo(0);
        assertThat(result.getResponse().getHeader("Location")).isNotNull()
                .contains("/api/v1/rooms/");
    }

    // =========================================================================
    // AC-2: Duplicate name in different casing returns 409 ROOM_NAME_ALREADY_EXISTS
    // =========================================================================

    @Test
    void ac2_duplicateNameCaseInsensitiveReturns409() throws Exception {
        String body1 = objectMapper.writeValueAsString(new CreateRoomRequestDto("Yoga Studio", 25));
        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        String body2 = objectMapper.writeValueAsString(new CreateRoomRequestDto("yoga studio", 25));
        MvcResult conflict = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                conflict.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.ROOM_NAME_ALREADY_EXISTS);
    }

    // =========================================================================
    // AC-3: capacity of 0 or 501 returns 422 OUT_OF_RANGE
    // =========================================================================

    @Test
    void ac3_capacityZeroReturns422OutOfRange() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateRoomRequestDto("Invalid Room", 0));

        MvcResult result = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotEmpty();
    }

    @Test
    void ac3_capacityFiveHundredOneReturns422OutOfRange() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateRoomRequestDto("Invalid Room", 501));

        MvcResult result = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotEmpty();
    }

    // =========================================================================
    // AC-4: minCapacity=20 returns only rooms with capacity 20 or above
    // =========================================================================

    @Test
    void ac4_listWithMinCapacityFilter() throws Exception {
        // Create rooms with different capacities
        createRoom("Small Room", 10);
        createRoom("Medium Room", 20);
        createRoom("Large Room", 50);

        MvcResult result = mockMvc.perform(get("/api/v1/rooms?minCapacity=20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                PageResponse.class
        );

        List<RoomResponse> rooms = page.content().stream()
                .map(item -> objectMapper.convertValue(item, RoomResponse.class))
                .toList();

        assertThat(rooms).allMatch(r -> r.capacity() >= 20);
        assertThat(rooms).extracting(RoomResponse::name)
                .contains("Medium Room", "Large Room")
                .doesNotContain("Small Room");
    }

    // =========================================================================
    // AC-5: List defaults to active only
    // =========================================================================

    @Test
    void ac5_listDefaultsToActiveOnly() throws Exception {
        // Create an active room
        String activeBody = objectMapper.writeValueAsString(new CreateRoomRequestDto("Active Test Room", 25));
        MvcResult activeResult = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activeBody))
                .andExpect(status().isCreated())
                .andReturn();
        RoomResponse activeRoom = objectMapper.readValue(
                activeResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        // Create and deactivate a room
        String createBody = objectMapper.writeValueAsString(new CreateRoomRequestDto("Inactive Test Room", 25));
        MvcResult createResult = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        RoomResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        mockMvc.perform(post("/api/v1/rooms/" + created.id() + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // List without includeInactive parameter
        MvcResult listResult = mockMvc.perform(get("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> page = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                PageResponse.class
        );

        List<RoomResponse> allRooms = page.content().stream()
                .map(item -> objectMapper.convertValue(item, RoomResponse.class))
                .toList();

        // Should only contain active rooms
        assertThat(allRooms).allMatch(RoomResponse::active);
        assertThat(allRooms).extracting(RoomResponse::id).contains(activeRoom.id(), roomId);
        assertThat(allRooms).extracting(RoomResponse::id).doesNotContain(created.id());
    }

    // =========================================================================
    // AC-6: PATCH increasing capacity always succeeds
    // =========================================================================

    @Test
    void ac6_patchIncreasingCapacityAlwaysSucceeds() throws Exception {
        // Fetch current room to get version
        MvcResult getResult = mockMvc.perform(get("/api/v1/rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse current = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        // PATCH to increase capacity
        String patchBody = objectMapper.writeValueAsString(new PatchRoomRequestDto(50, current.version()));
        MvcResult patchResult = mockMvc.perform(patch("/api/v1/rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse patched = objectMapper.readValue(
                patchResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        assertThat(patched.capacity()).isEqualTo(50);
        assertThat(patched.version()).isEqualTo(current.version() + 1);
    }

    // =========================================================================
    // AC-7: PATCH reducing capacity below a future session's capacity returns 409
    // =========================================================================

    @Test
    void ac7_patchReducingCapacityBelowFutureSessionCapacityReturns409() throws Exception {
        Room room = createRoomEntity("Capacity Test Room", 50);
        UUID testRoomId = room.getId();

        // Create a future session with capacity 30
        createFutureSession(testRoomId, baseTime.plusSeconds(3600), 30);

        // Fetch room to get version
        MvcResult getResult = mockMvc.perform(get("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse current = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        // Try to reduce capacity below 30
        String patchBody = objectMapper.writeValueAsString(new PatchRoomRequestDto(25, current.version()));
        MvcResult result = mockMvc.perform(patch("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.ROOM_HAS_FUTURE_SESSIONS);
        assertThat(env.detail()).contains("Cannot reduce capacity");
    }

    // =========================================================================
    // AC-8: PATCH reducing capacity when all future sessions are within new value succeeds
    // =========================================================================

    @Test
    void ac8_patchReducingCapacityWhenAllFutureSessionsWithinNewValueSucceeds() throws Exception {
        Room room = createRoomEntity("Capacity Safe Reduce", 50);
        UUID testRoomId = room.getId();

        // Create a future session with capacity 20
        createFutureSession(testRoomId, baseTime.plusSeconds(3600), 20);

        // Fetch room to get version
        MvcResult getResult = mockMvc.perform(get("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse current = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        // Reduce capacity to 30 (safe because session only needs 20)
        String patchBody = objectMapper.writeValueAsString(new PatchRoomRequestDto(30, current.version()));
        MvcResult patchResult = mockMvc.perform(patch("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse patched = objectMapper.readValue(
                patchResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        assertThat(patched.capacity()).isEqualTo(30);
    }

    // =========================================================================
    // AC-9: PATCH reducing capacity when only over-capacity session is CANCELLED succeeds
    // =========================================================================

    @Test
    void ac9_patchReducingCapacityWhenOnlyOverCapacitySessionIsCancelledSucceeds() throws Exception {
        Room room = createRoomEntity("Capacity Cancelled", 50);
        UUID testRoomId = room.getId();

        // Create a future session with capacity 40 and cancel it
        ClassSession session = createFutureSession(testRoomId, baseTime.plusSeconds(3600), 40);
        session.setStatus("CANCELLED");
        classSessionRepository.save(session);

        // Fetch room to get version
        MvcResult getResult = mockMvc.perform(get("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse current = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        // Reduce capacity to 30 (should succeed because only cancelled session needed more)
        String patchBody = objectMapper.writeValueAsString(new PatchRoomRequestDto(30, current.version()));
        MvcResult patchResult = mockMvc.perform(patch("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse patched = objectMapper.readValue(
                patchResult.getResponse().getContentAsString(),
                RoomResponse.class
        );

        assertThat(patched.capacity()).isEqualTo(30);
    }

    // =========================================================================
    // AC-10: PATCH with stale version returns 409
    // =========================================================================

    @Test
    void ac10_patchWithStaleVersionReturns409() throws Exception {
        // Fetch current room
        MvcResult getResult1 = mockMvc.perform(get("/api/v1/rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse current = objectMapper.readValue(
                getResult1.getResponse().getContentAsString(),
                RoomResponse.class
        );

        // First update to bump version
        String patch1Body = objectMapper.writeValueAsString(new PatchRoomRequestDto(35, current.version()));
        mockMvc.perform(patch("/api/v1/rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch1Body))
                .andExpect(status().isOk());

        // Try to update with stale version
        String patch2Body = objectMapper.writeValueAsString(new PatchRoomRequestDto(40, current.version()));
        MvcResult staleResult = mockMvc.perform(patch("/api/v1/rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch2Body))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                staleResult.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);

        // Verify only first update was applied
        MvcResult getResult2 = mockMvc.perform(get("/api/v1/rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse updated = objectMapper.readValue(
                getResult2.getResponse().getContentAsString(),
                RoomResponse.class
        );
        assertThat(updated.capacity()).isEqualTo(35);
    }

    // =========================================================================
    // AC-11: Deactivating a room with no future sessions succeeds
    // =========================================================================

    @Test
    void ac11_deactivatingRoomWithNoFutureSessionsSucceeds() throws Exception {
        Room room = createRoomEntity("No Sessions Room", 25);
        UUID testRoomId = room.getId();

        MvcResult result = mockMvc.perform(post("/api/v1/rooms/" + testRoomId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RoomResponse.class
        );

        assertThat(response.active()).isFalse();
    }

    // =========================================================================
    // AC-12: Deactivating a room with future SCHEDULED session returns 409 with count and earliest start
    // =========================================================================

    @Test
    void ac12_deactivatingRoomWithFutureScheduledSessionReturns409WithCountAndEarliestStart() throws Exception {
        Room room = createRoomEntity("Sessions Room", 25);
        UUID testRoomId = room.getId();

        // Create a future session
        Instant sessionStart = baseTime.plusSeconds(3600);
        createFutureSession(testRoomId, sessionStart, 20);

        MvcResult result = mockMvc.perform(post("/api/v1/rooms/" + testRoomId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.ROOM_HAS_FUTURE_SESSIONS);
        assertThat(env.detail()).contains("1", "future scheduled session(s)");
        assertThat(env.detail()).contains(sessionStart.toString());
    }

    // =========================================================================
    // AC-13: Deactivating a room whose future sessions are all CANCELLED succeeds
    // =========================================================================

    @Test
    void ac13_deactivatingRoomWithOnlyFutureCancelledSessionsSucceeds() throws Exception {
        Room room = createRoomEntity("Cancelled Sessions Room", 25);
        UUID testRoomId = room.getId();

        // Create a future session and cancel it
        ClassSession session = createFutureSession(testRoomId, baseTime.plusSeconds(3600), 20);
        session.setStatus("CANCELLED");
        classSessionRepository.save(session);

        MvcResult result = mockMvc.perform(post("/api/v1/rooms/" + testRoomId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RoomResponse.class
        );

        assertThat(response.active()).isFalse();
    }

    // =========================================================================
    // AC-14: Deactivating an already-inactive room returns 409 ROOM_INACTIVE
    // =========================================================================

    @Test
    void ac14_deactivatingAlreadyInactiveRoomReturns409() throws Exception {
        Room room = createRoomEntity("Already Inactive", 25);
        UUID testRoomId = room.getId();

        // Deactivate once
        mockMvc.perform(post("/api/v1/rooms/" + testRoomId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Try to deactivate again
        MvcResult result = mockMvc.perform(post("/api/v1/rooms/" + testRoomId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.ROOM_INACTIVE);
    }

    // =========================================================================
    // AC-16: A session in a now-inactive room still resolves on GET
    // =========================================================================

    @Test
    void ac16_sessionInNowInactiveRoomStillResolvesOnGet() throws Exception {
        Room room = createRoomEntity("Room To Deactivate", 25);
        UUID testRoomId = room.getId();

        // Create a future session
        ClassSession session = createFutureSession(testRoomId, baseTime.plusSeconds(3600), 20);

        // Deactivate the room
        mockMvc.perform(post("/api/v1/rooms/" + testRoomId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify room is inactive
        MvcResult getRoomResult = mockMvc.perform(get("/api/v1/rooms/" + testRoomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        RoomResponse roomResp = objectMapper.readValue(
                getRoomResult.getResponse().getContentAsString(),
                RoomResponse.class
        );
        assertThat(roomResp.active()).isFalse();

        // Verify the session is still in the database (not deleted)
        ClassSession retrievedSession = classSessionRepository.findById(session.getId()).orElse(null);
        assertThat(retrievedSession).isNotNull();
        assertThat(retrievedSession.getRoomId()).isEqualTo(testRoomId);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void createRoom(String name, int capacity) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateRoomRequestDto(name, capacity));
        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private Room createRoomEntity(String name, int capacity) {
        Room room = new Room(name, capacity);
        return roomRepository.save(room);
    }

    private ClassSession createFutureSession(UUID roomId, Instant startsAt, int capacity) {
        // Create a class type
        ClassType classType = new ClassType("Test Class Type", "Test", 60, 50);
        classType = classTypeRepository.save(classType);

        // Create an instructor
        Instructor instructor = new Instructor("test@example.com", "Test Instructor", "Bio", List.of());
        instructor = instructorRepository.save(instructor);

        // Create a session
        Instant endsAt = startsAt.plusSeconds(3600);
        ClassSession session = new ClassSession(classType.getId(), instructor.getId(), roomId, startsAt, endsAt, capacity);
        return classSessionRepository.save(session);
    }

    // =========================================================================
    // Test DTOs (mirroring the actual DTOs for testing purposes)
    // =========================================================================

    static class CreateRoomRequestDto {
        public String name;
        public int capacity;

        CreateRoomRequestDto(String name, int capacity) {
            this.name = name;
            this.capacity = capacity;
        }
    }

    static class PatchRoomRequestDto {
        public Integer capacity;
        public Long version;

        PatchRoomRequestDto(Integer capacity, Long version) {
            this.capacity = capacity;
            this.version = version;
        }
    }
}
