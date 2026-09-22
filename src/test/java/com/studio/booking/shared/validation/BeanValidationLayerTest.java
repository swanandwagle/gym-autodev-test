package com.studio.booking.shared.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.error.FieldError;
import com.studio.booking.shared.error.GlobalExceptionHandler;
import com.studio.booking.shared.web.CorrelationFilter;
import com.studio.booking.shared.web.StrictJsonConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for GYM-20: Bean Validation layer, custom constraints & strict JSON parsing.
 *
 * Covers AC-1 through AC-9.
 */
@WebMvcTest(
        controllers = BeanValidationLayerTest.TestController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@Import({GlobalExceptionHandler.class, CorrelationFilter.class, StrictJsonConfig.class})
@TestPropertySource(properties = "studio.api.base-url=https://api.studio.example")
class BeanValidationLayerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // =========================================================================
    // Test controller — exposes endpoints to exercise each validation scenario
    // =========================================================================

    @RestController
    @RequestMapping("/test/validation")
    @Validated
    static class TestController {

        /** AC-1, AC-2, AC-3: multi-constraint DTO with various constraint types */
        record MultiConstraintInput(
                @NotBlank String name,
                @NotNull @Min(1) @Max(100) Integer age,
                @Email String email,
                @Size(min = 3, max = 10) String username,
                @Pattern(regexp = "^[A-Z]{2,3}$") String code
        ) {}

        /** AC-4: DTO for strict unknown-field rejection */
        record StrictInput(
                @NotBlank String name
        ) {}

        /** AC-5: nested DTO with a list of items */
        record ItemInput(
                @NotBlank String label
        ) {}

        record NestedInput(
                @NotBlank String title,
                @Valid @NotNull List<ItemInput> items
        ) {}

        /** AC-7: enum type for enum validation */
        enum Status { ACTIVE, INACTIVE }

        record EnumInput(
                @NotNull Status status
        ) {}

        /** AC-8: timestamp binding */
        record TimestampInput(
                @NotNull Instant startsAt
        ) {}

        @PostMapping("/multi")
        String multiConstraint(@Valid @RequestBody MultiConstraintInput body) { return "ok"; }

        @PostMapping("/strict")
        String strictJson(@Valid @RequestBody StrictInput body) { return "ok"; }

        @PostMapping("/nested")
        String nested(@Valid @RequestBody NestedInput body) { return "ok"; }

        @GetMapping("/uuid/{id}")
        String uuidPath(@ValidUuid @PathVariable String id) { return id; }

        @PostMapping("/enum")
        String enumInput(@Valid @RequestBody EnumInput body) { return body.status().name(); }

        @PostMapping("/timestamp")
        Instant timestampInput(@Valid @RequestBody TimestampInput body) { return body.startsAt(); }
    }

    // =========================================================================
    // AC-1: Five constraint violations → single 422 listing all five
    // =========================================================================

    @Test
    void testAc1MultipleConstraintViolationsAllReported() throws Exception {
        // name=blank, age=null, email=invalid, username=1char (too short), code=lowercase (pattern fail)
        String json = """
                {"name":"","age":null,"email":"not-an-email","username":"x","code":"lowercase"}
                """;

        MvcResult result = mockMvc.perform(post("/test/validation/multi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(422);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().hasSizeGreaterThanOrEqualTo(5);
    }

    // =========================================================================
    // AC-2: Constraint code mappings — one assertion per constraint annotation
    // =========================================================================

    @Test
    void testAc2NotBlankConstraintMapsToNotBlank() throws Exception {
        String json = """
                {"name":"","age":1,"email":"a@b.com","username":"abc","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "name", "NOTBLANK");
    }

    @Test
    void testAc2NotNullConstraintMapsToNotNull() throws Exception {
        String json = """
                {"name":"Alice","age":null,"email":"a@b.com","username":"abc","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "age", "NOTNULL");
    }

    @Test
    void testAc2MinConstraintMapsToMin() throws Exception {
        String json = """
                {"name":"Alice","age":0,"email":"a@b.com","username":"abc","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "age", "MIN");
    }

    @Test
    void testAc2MaxConstraintMapsToMax() throws Exception {
        String json = """
                {"name":"Alice","age":200,"email":"a@b.com","username":"abc","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "age", "MAX");
    }

    @Test
    void testAc2EmailConstraintMapsToEmail() throws Exception {
        String json = """
                {"name":"Alice","age":25,"email":"not-an-email","username":"abc","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "email", "EMAIL");
    }

    @Test
    void testAc2PatternConstraintMapsToPattern() throws Exception {
        String json = """
                {"name":"Alice","age":25,"email":"a@b.com","username":"abc","code":"lowercase"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "code", "PATTERN");
    }

    // =========================================================================
    // AC-3: @Size produces TOO_SHORT or TOO_LONG depending on bound breached
    // =========================================================================

    @Test
    void testAc3SizeViolationTooShort() throws Exception {
        // username min=3, send "ab" (2 chars → too short)
        String json = """
                {"name":"Alice","age":25,"email":"a@b.com","username":"ab","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "username", "TOO_SHORT");
    }

    @Test
    void testAc3SizeViolationTooLong() throws Exception {
        // username max=10, send 11 chars → too long
        String json = """
                {"name":"Alice","age":25,"email":"a@b.com","username":"abcdefghijk","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        assertFieldCode(env, "username", "TOO_LONG");
    }

    // =========================================================================
    // AC-4: Unknown JSON property → 422 UNKNOWN_FIELD; malformed JSON → 400
    // =========================================================================

    @Test
    void testAc4UnknownFieldReturns422WithUnknownFieldEntry() throws Exception {
        // "name" is valid; "surprise" is unknown
        String json = """
                {"name":"Alice","surprise":"intruder"}
                """;
        MvcResult result = mockMvc.perform(post("/test/validation/strict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(422);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();

        FieldError unknownFieldError = env.errors().stream()
                .filter(fe -> "UNKNOWN_FIELD".equals(fe.code()))
                .findFirst()
                .orElse(null);
        assertThat(unknownFieldError).isNotNull();
        assertThat(unknownFieldError.field()).isEqualTo("surprise");
    }

    @Test
    void testAc4MalformedJsonReturns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validation/strict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(400);
        assertThat(env.code()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
        // Must not have errors[] (malformed is not a field-level error)
        assertThat(env.errors()).isNull();
    }

    @Test
    void testAc4UnknownFieldDistinguishableFromMalformedJson() throws Exception {
        // unknown field → 422, malformed → 400; statuses must differ
        MvcResult unknownResult = mockMvc.perform(post("/test/validation/strict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"extra\":\"val\"}"))
                .andReturn();
        MvcResult malformedResult = mockMvc.perform(post("/test/validation/strict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andReturn();

        assertThat(unknownResult.getResponse().getStatus()).isEqualTo(422);
        assertThat(malformedResult.getResponse().getStatus()).isEqualTo(400);
    }

    // =========================================================================
    // AC-5: Nested field violation reports full JSON path including collection index
    // =========================================================================

    @Test
    void testAc5NestedFieldViolationReportsFullJsonPath() throws Exception {
        // items[0].label is blank → should report "items[0].label"
        String json = """
                {"title":"MyTitle","items":[{"label":""}]}
                """;
        MvcResult result = mockMvc.perform(post("/test/validation/nested")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.errors()).isNotNull().isNotEmpty();

        boolean foundNestedPath = env.errors().stream()
                .anyMatch(fe -> fe.field() != null && fe.field().contains("items") && fe.field().contains("label"));
        assertThat(foundNestedPath)
                .as("Expected a field error with a nested path containing 'items' and 'label', got: %s",
                        env.errors())
                .isTrue();
    }

    @Test
    void testAc5NestedFieldViolationIncludesCollectionIndex() throws Exception {
        // second item (index 1) has blank label
        String json = """
                {"title":"MyTitle","items":[{"label":"ok"},{"label":""}]}
                """;
        MvcResult result = mockMvc.perform(post("/test/validation/nested")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.errors()).isNotNull().isNotEmpty();

        // The path must contain "[1]" to identify the second element
        boolean containsIndex = env.errors().stream()
                .anyMatch(fe -> fe.field() != null && fe.field().contains("[1]"));
        assertThat(containsIndex)
                .as("Expected field path containing collection index '[1]', got: %s", env.errors())
                .isTrue();
    }

    // =========================================================================
    // AC-6: Malformed UUID in path variable → 422 INVALID_FORMAT, never 500
    // =========================================================================

    @Test
    void testAc6MalformedUuidPathVariableReturns422InvalidFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/validation/uuid/not-a-uuid"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(422);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();
        assertThat(env.errors().getFirst().code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void testAc6ValidUuidPathVariableReturns200() throws Exception {
        mockMvc.perform(get("/test/validation/uuid/550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // AC-7: Invalid enum value → INVALID_ENUM with accepted values listed
    // =========================================================================

    @Test
    void testAc7InvalidEnumValueReturnsInvalidEnumWithAcceptedValues() throws Exception {
        String json = """
                {"status":"UNKNOWN_STATUS"}
                """;
        MvcResult result = mockMvc.perform(post("/test/validation/enum")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(422);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();

        FieldError enumError = env.errors().stream()
                .filter(fe -> "INVALID_ENUM".equals(fe.code()))
                .findFirst()
                .orElse(null);
        assertThat(enumError).isNotNull();
        // Message must list accepted values
        assertThat(enumError.message()).containsIgnoringCase("ACTIVE");
        assertThat(enumError.message()).containsIgnoringCase("INACTIVE");
    }

    // =========================================================================
    // AC-8: Non-UTC offset timestamp binds correctly and resource returns UTC
    // =========================================================================

    @Test
    void testAc8NonUtcOffsetTimestampBindsCorrectlyAndReturnsUtc() throws Exception {
        // +05:30 is IST; 2026-09-22T10:00:00+05:30 = 2026-09-22T04:30:00Z
        String json = """
                {"startsAt":"2026-09-22T10:00:00+05:30"}
                """;
        MvcResult result = mockMvc.perform(post("/test/validation/timestamp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Response must be in UTC (ends with Z)
        assertThat(body).contains("Z");
        // The UTC value must represent the same instant: 04:30:00Z
        assertThat(body).contains("2026-09-22T04:30:00Z");
    }

    @Test
    void testAc8UtcOffsetTimestampAlsoBindsCorrectly() throws Exception {
        String json = """
                {"startsAt":"2026-09-22T12:00:00Z"}
                """;
        MvcResult result = mockMvc.perform(post("/test/validation/timestamp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("2026-09-22T12:00:00Z");
    }

    // =========================================================================
    // AC-9: rejectedValue populated and long strings truncated
    // =========================================================================

    @Test
    void testAc9RejectedValueIsPopulatedForFieldErrors() throws Exception {
        String json = """
                {"name":"","age":1,"email":"a@b.com","username":"abc","code":"AB"}
                """;
        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        FieldError nameError = env.errors().stream()
                .filter(fe -> "name".equals(fe.field()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No error for 'name'"));

        // rejectedValue should be present (empty string is the rejected value)
        assertThat(nameError.rejectedValue()).isNotNull();
    }

    @Test
    void testAc9LongRejectedValueIsTruncated() throws Exception {
        // Send a very long value that violates @Email — it should be truncated in rejectedValue
        String longValue = "a".repeat(300) + "@example.com";
        String json = String.format(
                "{\"name\":\"Alice\",\"age\":25,\"email\":\"%s\",\"username\":\"abc\",\"code\":\"AB\"}", longValue);

        MvcResult result = performMulti(json);
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.errors()).isNotNull();
        FieldError emailError = env.errors().stream()
                .filter(fe -> "email".equals(fe.field()))
                .findFirst()
                .orElse(null);

        if (emailError != null && emailError.rejectedValue() != null) {
            String rejectedStr = emailError.rejectedValue().toString();
            assertThat(rejectedStr.length())
                    .as("rejectedValue must be truncated to at most 201 characters (200 + ellipsis)")
                    .isLessThanOrEqualTo(201);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private MvcResult performMulti(String json) throws Exception {
        return mockMvc.perform(post("/test/validation/multi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
    }

    private ErrorEnvelope parseEnvelope(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorEnvelope.class);
    }

    private void assertFieldCode(ErrorEnvelope env, String field, String expectedCode) {
        boolean found = env.errors().stream()
                .anyMatch(fe -> field.equals(fe.field()) && expectedCode.equals(fe.code()));
        assertThat(found)
                .as("Expected field '%s' with code '%s' in errors: %s", field, expectedCode, env.errors())
                .isTrue();
    }
}
