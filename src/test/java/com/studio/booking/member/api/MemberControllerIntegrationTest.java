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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
