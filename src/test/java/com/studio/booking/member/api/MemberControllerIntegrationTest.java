package com.studio.booking.member.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.member.infrastructure.MemberRepository;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void test_ac1_register_returns_201_with_location_and_body() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "john@example.com",
            "John Doe",
            "+1234567890"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

        MemberResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.fullName()).isEqualTo("John Doe");
        assertThat(response.phone()).isEqualTo("+1234567890");

        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains("/api/v1/members/").endsWith(response.id());
    }

    @Test
    void test_ac2_member_created_with_active_status_null_suspension_version_zero() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "alice@example.com",
            "Alice",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.suspensionReason()).isNull();
        assertThat(response.version()).isEqualTo(0);
    }

    @Test
    void test_ac3_duplicate_email_case_insensitive_returns_409() throws Exception {
        RegisterMemberRequest firstRequest = new RegisterMemberRequest(
            "priya.k@example.com",
            "Priya K",
            null
        );

        mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(firstRequest)))
            .andExpect(status().isCreated());

        RegisterMemberRequest secondRequest = new RegisterMemberRequest(
            "Priya.K@example.com",
            "Priya K",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(secondRequest)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS);
        assertThat(error.status()).isEqualTo(409);
    }

    @Test
    void test_ac4_email_casing_preserved() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "John.Doe@Example.COM",
            "John Doe",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(response.email()).isEqualTo("John.Doe@Example.COM");
    }

    @Test
    void test_ac5_validation_email_required() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "",
            "John Doe",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.errors()).isNotEmpty();
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.field()).isEqualTo("email")
                .as("email field should be present in errors")
        );
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.code()).isEqualTo("NOTBLANK")
                .as("email validation should return NOTBLANK code")
        );
    }

    @Test
    void test_ac5_validation_email_format_invalid() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "not-an-email",
            "John Doe",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.errors()).isNotEmpty();
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.field()).isEqualTo("email")
                .as("email field should be present in errors")
        );
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.code()).isEqualTo("EMAIL")
                .as("email validation should return EMAIL code")
        );
    }

    @Test
    void test_ac5_validation_fullname_required() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "john@example.com",
            "",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.errors()).isNotEmpty();
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.field()).isEqualTo("fullName")
                .as("fullName field should be present in errors")
        );
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.code()).isEqualTo("NOTBLANK")
                .as("fullName validation should return NOTBLANK code")
        );
    }

    @Test
    void test_ac6_multiple_validation_errors_returned() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "invalid-email",
            "",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.errors()).hasSize(2);
        assertThat(error.errors())
            .extracting("field")
            .containsExactlyInAnyOrder("email", "fullName");
    }

    @Test
    void test_ac7_unknown_field_status_returns_422_unknown_field() throws Exception {
        String json = """
            {
                "email": "john@example.com",
                "fullName": "John Doe",
                "status": "ACTIVE"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN_FIELD);
    }

    @Test
    void test_ac8_joined_at_reflects_frozen_test_clock() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "test@example.com",
            "Test User",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(response.joinedAt()).isNotNull();
    }

    @Test
    void test_ac9_whitespace_trimmed() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "john@example.com",
            "  John Doe  ",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(response.fullName()).isEqualTo("John Doe");
    }

    @Test
    void test_ac9_only_whitespace_fails_as_required() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
            "john@example.com",
            "   ",
            null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.errors()).isNotEmpty();
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.field()).isEqualTo("fullName")
        );
    }

    // =========================================================================
    // GYM-26: GET and PATCH endpoints
    // =========================================================================

    @Test
    void test_ac1_get_existing_member_returns_200_with_full_representation() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "alice@example.com",
            "Alice Smith",
            "+1234567890"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        MvcResult getResult = mockMvc.perform(get("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse retrieved = objectMapper.readValue(
            getResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(retrieved.id()).isEqualTo(registered.id());
        assertThat(retrieved.email()).isEqualTo("alice@example.com");
        assertThat(retrieved.fullName()).isEqualTo("Alice Smith");
        assertThat(retrieved.phone()).isEqualTo("+1234567890");
        assertThat(retrieved.status()).isEqualTo("ACTIVE");
        assertThat(retrieved.suspensionReason()).isNull();
        assertThat(retrieved.version()).isEqualTo(0);
    }

    @Test
    void test_ac2_get_random_uuid_returns_404_member_not_found() throws Exception {
        String randomUuid = "00000000-0000-0000-0000-000000000000";

        MvcResult result = mockMvc.perform(get("/api/v1/members/{id}", randomUuid)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(error.status()).isEqualTo(404);
    }

    @Test
    void test_ac3_get_malformed_uuid_returns_422_invalid_format() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/members/{id}", "not-a-uuid")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.status()).isEqualTo(422);
        assertThat(error.errors()).isNotEmpty();
        assertThat(error.errors()).anySatisfy(fieldError ->
            assertThat(fieldError.code()).isEqualTo("INVALID_FORMAT")
        );
    }

    @Test
    void test_ac4_patch_only_phone_updates_phone_unchanged_others() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "bob@example.com",
            "Bob Jones",
            "+1111111111"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "phone": "+9999999999",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.phone()).isEqualTo("+9999999999");
        assertThat(updated.email()).isEqualTo("bob@example.com");
        assertThat(updated.fullName()).isEqualTo("Bob Jones");
    }

    @Test
    void test_ac4_patch_only_email_updates_email() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "carol@example.com",
            "Carol Lee",
            "+2222222222"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "email": "carol.new@example.com",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.email()).isEqualTo("carol.new@example.com");
        assertThat(updated.fullName()).isEqualTo("Carol Lee");
        assertThat(updated.phone()).isEqualTo("+2222222222");
    }

    @Test
    void test_ac4_patch_only_name_updates_name() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "diana@example.com",
            "Diana Prince",
            "+3333333333"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "fullName": "Diana Wonder",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.fullName()).isEqualTo("Diana Wonder");
        assertThat(updated.email()).isEqualTo("diana@example.com");
        assertThat(updated.phone()).isEqualTo("+3333333333");
    }

    @Test
    void test_ac5_patch_response_version_incremented_by_one() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "eve@example.com",
            "Eve Wilson",
            "+4444444444"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "phone": "+5555555555",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void test_ac6_patch_stale_version_returns_409_concurrent_modification() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "frank@example.com",
            "Frank Turner",
            "+6666666666"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson1 = """
            {
                "phone": "+7777777777",
                "version": 0
            }
            """;

        mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson1))
            .andExpect(status().isOk());

        String updateJson2 = """
            {
                "phone": "+8888888888",
                "version": 0
            }
            """;

        MvcResult staleResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson2))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            staleResult.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
        assertThat(error.status()).isEqualTo(409);
    }

    @Test
    void test_ac7_concurrent_patch_same_version_one_succeeds_one_409() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "grace@example.com",
            "Grace Hopper",
            "+7111111111"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "phone": "+9911111111",
                "version": 0
            }
            """;

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                        .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    void test_ac8_patch_email_to_another_member_email_returns_409() throws Exception {
        RegisterMemberRequest request1 = new RegisterMemberRequest(
            "henry@example.com",
            "Henry Ford",
            null
        );
        MvcResult result1 = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse member1 = objectMapper.readValue(
            result1.getResponse().getContentAsString(),
            MemberResponse.class
        );

        RegisterMemberRequest request2 = new RegisterMemberRequest(
            "iris@example.com",
            "Iris West",
            null
        );
        MvcResult result2 = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse member2 = objectMapper.readValue(
            result2.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "email": "iris@example.com",
                "version": 0
            }
            """;

        MvcResult updateResult = mockMvc.perform(patch("/api/v1/members/{id}", member1.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            updateResult.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS);
        assertThat(error.status()).isEqualTo(409);
    }

    @Test
    void test_ac9_patch_email_to_own_current_email_succeeds() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "jack@example.com",
            "Jack Ryan",
            "+8111111111"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "email": "jack@example.com",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.email()).isEqualTo("jack@example.com");
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void test_ac10_patch_only_version_returns_422_body_error() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "karl@example.com",
            "Karl Marx",
            "+9111111111"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.status()).isEqualTo(422);
        assertThat(error.errors()).isNotEmpty();
    }

    @Test
    void test_ac11_patch_status_field_returns_422_unknown_field() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "liam@example.com",
            "Liam Neeson",
            "+0111111111"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "status": "SUSPENDED",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN_FIELD);
        assertThat(error.status()).isEqualTo(422);
    }

    @Test
    void test_ac12_patch_suspended_member_succeeds_status_untouched() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "maya@example.com",
            "Maya Angelou",
            "+1112222222"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String updateJson = """
            {
                "phone": "+9999911111",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.phone()).isEqualTo("+9999911111");
        assertThat(updated.status()).isEqualTo("ACTIVE");
    }

    @Test
    void test_ac13_updated_at_advances_created_and_joined_unchanged() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "nathan@example.com",
            "Nathan Drake",
            "+1113333333"
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        java.time.Instant createdAtInitial = registered.createdAt();
        java.time.Instant joinedAtInitial = registered.joinedAt();
        java.time.Instant updatedAtInitial = registered.updatedAt();

        Thread.sleep(100);

        String updateJson = """
            {
                "phone": "+1114444444",
                "version": 0
            }
            """;

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/members/{id}", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse updated = objectMapper.readValue(
            patchResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(updated.createdAt()).isEqualTo(createdAtInitial);
        assertThat(updated.joinedAt()).isEqualTo(joinedAtInitial);
        assertThat(updated.updatedAt()).isAfter(updatedAtInitial);
    }

    // =========================================================================
    // GYM-27: Suspend and reactivate endpoints
    // =========================================================================

    @Test
    void test_ac1_suspend_active_member_returns_200_sets_status_reason_suspended_at() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "suspend@example.com",
            "Test Suspend",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        SuspendMemberRequest suspendRequest = new SuspendMemberRequest("STAFF");
        MvcResult suspendResult = mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse suspended = objectMapper.readValue(
            suspendResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(suspended.suspensionReason()).isEqualTo("STAFF");
        assertThat(suspended.suspendedAt()).isNotNull();
    }

    @Test
    void test_ac1_suspend_advances_version_and_updated_at() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "version@example.com",
            "Test Version",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        long originalVersion = registered.version();
        java.time.Instant originalUpdatedAt = registered.updatedAt();

        SuspendMemberRequest suspendRequest = new SuspendMemberRequest("STAFF");
        MvcResult suspendResult = mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse suspended = objectMapper.readValue(
            suspendResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(suspended.version()).isGreaterThan(originalVersion);
        assertThat(suspended.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    void test_ac2_suspend_already_suspended_returns_409() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "already@example.com",
            "Test Already",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        SuspendMemberRequest suspendRequest = new SuspendMemberRequest("STAFF");
        mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_ALREADY_SUSPENDED);
        assertThat(error.status()).isEqualTo(409);
    }

    @Test
    void test_ac3_reactivate_suspended_member_clears_reason_and_suspended_at() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "reactivate@example.com",
            "Test Reactivate",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        SuspendMemberRequest suspendRequest = new SuspendMemberRequest("STAFF");
        mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isOk());

        ReactivateMemberRequest reactivateRequest = new ReactivateMemberRequest();
        MvcResult reactivateResult = mockMvc.perform(post("/api/v1/members/{id}/reactivate", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(reactivateRequest)))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse reactivated = objectMapper.readValue(
            reactivateResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(reactivated.status()).isEqualTo("ACTIVE");
        assertThat(reactivated.suspensionReason()).isNull();
        assertThat(reactivated.suspendedAt()).isNull();
    }

    @Test
    void test_ac4_reactivate_active_member_returns_409() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "active@example.com",
            "Test Active",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        ReactivateMemberRequest reactivateRequest = new ReactivateMemberRequest();
        MvcResult result = mockMvc.perform(post("/api/v1/members/{id}/reactivate", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(reactivateRequest)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_NOT_SUSPENDED);
        assertThat(error.status()).isEqualTo(409);
    }

    @Test
    void test_ac5_suspend_unknown_member_returns_404() throws Exception {
        String unknownId = "00000000-0000-0000-0000-000000000000";
        SuspendMemberRequest suspendRequest = new SuspendMemberRequest("STAFF");

        MvcResult result = mockMvc.perform(post("/api/v1/members/{id}/suspend", unknownId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(error.status()).isEqualTo(404);
    }

    @Test
    void test_ac5_reactivate_unknown_member_returns_404() throws Exception {
        String unknownId = "00000000-0000-0000-0000-000000000000";
        ReactivateMemberRequest reactivateRequest = new ReactivateMemberRequest();

        MvcResult result = mockMvc.perform(post("/api/v1/members/{id}/reactivate", unknownId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(reactivateRequest)))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(error.status()).isEqualTo(404);
    }

    @Test
    void test_ac6_suspend_with_reason_over_255_chars_returns_422_too_long() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "toolong@example.com",
            "Test Too Long",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        String longReason = "a".repeat(256);
        SuspendMemberRequest suspendRequest = new SuspendMemberRequest(longReason);

        MvcResult result = mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.TOO_LONG);
        assertThat(error.status()).isEqualTo(422);
    }

    @Test
    void test_ac6_suspend_without_reason_succeeds() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "noreason@example.com",
            "Test No Reason",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        MvcResult suspendResult = mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse suspended = objectMapper.readValue(
            suspendResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(suspended.suspensionReason()).isNull();
    }

    @Test
    void test_ac8_staff_vs_no_show_reason_distinguishable() throws Exception {
        RegisterMemberRequest request1 = new RegisterMemberRequest(
            "staff@example.com",
            "Staff Suspend",
            null
        );
        MvcResult result1 = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse member1 = objectMapper.readValue(
            result1.getResponse().getContentAsString(),
            MemberResponse.class
        );

        RegisterMemberRequest request2 = new RegisterMemberRequest(
            "noshow@example.com",
            "NoShow Suspend",
            null
        );
        MvcResult result2 = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse member2 = objectMapper.readValue(
            result2.getResponse().getContentAsString(),
            MemberResponse.class
        );

        SuspendMemberRequest staffRequest = new SuspendMemberRequest("STAFF");
        MvcResult suspendStaffResult = mockMvc.perform(post("/api/v1/members/{id}/suspend", member1.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(staffRequest)))
            .andExpect(status().isOk())
            .andReturn();

        SuspendMemberRequest noShowRequest = new SuspendMemberRequest("NO_SHOW_LIMIT");
        MvcResult suspendNoShowResult = mockMvc.perform(post("/api/v1/members/{id}/suspend", member2.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(noShowRequest)))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse suspendedByStaff = objectMapper.readValue(
            suspendStaffResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        MemberResponse suspendedByNoShow = objectMapper.readValue(
            suspendNoShowResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(suspendedByStaff.suspensionReason()).isEqualTo("STAFF");
        assertThat(suspendedByNoShow.suspensionReason()).isEqualTo("NO_SHOW_LIMIT");
    }

    @Test
    void test_ac9_reactivated_member_can_book_again() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "rebook@example.com",
            "Test Rebook",
            null
        );
        MvcResult registerResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse registered = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        SuspendMemberRequest suspendRequest = new SuspendMemberRequest("NO_SHOW_LIMIT");
        mockMvc.perform(post("/api/v1/members/{id}/suspend", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(suspendRequest)))
            .andExpect(status().isOk());

        ReactivateMemberRequest reactivateRequest = new ReactivateMemberRequest();
        MvcResult reactivateResult = mockMvc.perform(post("/api/v1/members/{id}/reactivate", registered.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(reactivateRequest)))
            .andExpect(status().isOk())
            .andReturn();

        MemberResponse reactivated = objectMapper.readValue(
            reactivateResult.getResponse().getContentAsString(),
            MemberResponse.class
        );

        assertThat(reactivated.status()).isEqualTo("ACTIVE");
        assertThat(reactivated.suspensionReason()).isNull();
    }
}
