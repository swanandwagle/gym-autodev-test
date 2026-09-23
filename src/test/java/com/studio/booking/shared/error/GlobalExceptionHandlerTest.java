package com.studio.booking.shared.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.shared.web.CorrelationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for GlobalExceptionHandler covering all five error origin paths.
 *
 * The five paths tested (per story AC-1):
 *   1. Bean-validation failure
 *   2. ApiException from application service
 *   3. DataIntegrityViolationException (database constraint)
 *   4. OptimisticLockingFailureException (optimistic lock)
 *   5. Unhandled RuntimeException
 *
 * Additional tests cover AC-2 through AC-9.
 */
@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@Import({GlobalExceptionHandler.class, CorrelationFilter.class, ConstraintViolationTranslator.class})
@TestPropertySource(properties = "studio.api.base-url=https://api.studio.example")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // =========================================================================
    // Test controller — triggers each error origin
    // =========================================================================

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record Input(@NotBlank String name) {}

        @PostMapping("/validation")
        String validation(@Valid @RequestBody Input body) { return "ok"; }

        @PostMapping("/api-exception")
        String apiException(@RequestBody Map<String, String> body) {
            String type = body.get("type");
            return switch (type) {
                case "notFound"     -> { throw ApiException.notFound("Member not found"); }
                case "conflict"     -> { throw ApiException.conflict("Session is full"); }
                case "notPermitted" -> { throw ApiException.notPermitted("Member suspended"); }
                case "concurrent"   -> { throw ApiException.concurrentModification("Stale data"); }
                default             -> { throw ApiException.notFound("Unknown"); }
            };
        }

        @PostMapping("/db-constraint")
        String dbConstraint() { throw new DataIntegrityViolationException("unique constraint"); }

        @PostMapping("/optimistic-lock")
        String optimisticLock() { throw new ObjectOptimisticLockingFailureException(Object.class, "1"); }

        @PostMapping("/unhandled")
        String unhandled() { throw new RuntimeException("something went wrong internally"); }
    }

    // =========================================================================
    // AC-1 path 1: Bean-validation failure
    // =========================================================================

    @Test
    void testAc1EnvelopeShapeFromBeanValidation() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertCommonFields(env, 422, ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();
        assertThat(env.type()).isEqualTo("https://api.studio.example/errors/validation-failed");
    }

    // =========================================================================
    // AC-1 path 2: ApiException from application service
    // =========================================================================

    @Test
    void testAc1EnvelopeShapeFromApiException() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/api-exception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"notFound\"}"))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertCommonFields(env, 404, ErrorCode.NOT_FOUND);
        assertThat(env.errors()).isNull();
    }

    // =========================================================================
    // AC-1 path 3: DataIntegrityViolationException (database constraint)
    // =========================================================================

    @Test
    void testAc1EnvelopeShapeFromDatabaseConstraint() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/db-constraint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn();

        // Unmapped constraint (no PSQLException chain) → CONCURRENT_MODIFICATION fallback
        ErrorEnvelope env = parseEnvelope(result);
        assertCommonFields(env, 409, ErrorCode.CONCURRENT_MODIFICATION);
        assertThat(env.errors()).isNull();
    }

    // =========================================================================
    // AC-1 path 4: OptimisticLockingFailureException
    // =========================================================================

    @Test
    void testAc1EnvelopeShapeFromOptimisticLock() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/optimistic-lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertCommonFields(env, 409, ErrorCode.CONCURRENT_MODIFICATION);
        assertThat(env.errors()).isNull();
    }

    // =========================================================================
    // AC-1 path 5: Unhandled RuntimeException
    // =========================================================================

    @Test
    void testAc1EnvelopeShapeFromUnhandledRuntime() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/unhandled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertCommonFields(env, 500, ErrorCode.INTERNAL_ERROR);
        assertThat(env.errors()).isNull();
    }

    // =========================================================================
    // AC-2: Unhandled → 500 INTERNAL_ERROR, no internal detail
    // =========================================================================

    @Test
    void testAc2UnhandledExceptionReturns500WithNoInternalDetail() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/unhandled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        ErrorEnvelope env = parseEnvelope(result);

        assertThat(env.status()).isEqualTo(500);
        assertThat(env.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(env.detail()).isEqualTo(ErrorMessages.forCode(ErrorCode.INTERNAL_ERROR));

        // Must not contain stack trace, SQL, internal class name or package
        assertThat(body).doesNotContain("RuntimeException");
        assertThat(body).doesNotContain("java.lang");
        assertThat(body).doesNotContain("com.studio.booking.shared.error.GlobalExceptionHandlerTest");
        assertThat(body).doesNotContain("at ");
        assertThat(body).doesNotContain("something went wrong internally");
    }

    // =========================================================================
    // AC-4: X-Request-Id is echoed in response header and traceId
    // =========================================================================

    @Test
    void testAc4XRequestIdPassthroughToResponseHeaderAndTraceId() throws Exception {
        String requestId = "my-correlation-id-abc123";

        MvcResult result = mockMvc.perform(post("/test/unhandled")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", requestId))
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.traceId()).isEqualTo(requestId);
    }

    // =========================================================================
    // AC-5: Request without X-Request-Id gets a generated traceId
    // =========================================================================

    @Test
    void testAc5GeneratedTraceIdPresentWhenNoHeader() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/unhandled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.traceId()).isNotNull().isNotBlank();

        String responseHeader = result.getResponse().getHeader("X-Request-Id");
        assertThat(responseHeader).isNotNull().isNotBlank();
        assertThat(env.traceId()).isEqualTo(responseHeader);
    }

    // =========================================================================
    // AC-6: errors[] present on 422, absent on others
    // =========================================================================

    @Test
    void testAc6ErrorsArrayPresentOn422AbsentOnOthers() throws Exception {
        // 422 — must have errors
        MvcResult validationResult = mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        ErrorEnvelope validationEnv = parseEnvelope(validationResult);
        assertThat(validationEnv.errors()).isNotNull().isNotEmpty();

        // 404 — must NOT have errors (not null, not empty array)
        MvcResult notFoundResult = mockMvc.perform(post("/test/api-exception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"notFound\"}"))
                .andExpect(status().isNotFound())
                .andReturn();
        String notFoundBody = notFoundResult.getResponse().getContentAsString();
        assertThat(notFoundBody).doesNotContain("\"errors\"");

        // 409 — must NOT have errors
        MvcResult conflictResult = mockMvc.perform(post("/test/db-constraint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn();
        String conflictBody = conflictResult.getResponse().getContentAsString();
        assertThat(conflictBody).doesNotContain("\"errors\"");

        // 500 — must NOT have errors
        MvcResult serverErrorResult = mockMvc.perform(post("/test/unhandled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String serverErrorBody = serverErrorResult.getResponse().getContentAsString();
        assertThat(serverErrorBody).doesNotContain("\"errors\"");
    }

    // =========================================================================
    // AC-7: type URI is stable and derived from code
    // =========================================================================

    @Test
    void testAc7TypeUriDerivedFromCode() throws Exception {
        // Each error code should produce a predictable type URI
        MvcResult conflictResult = mockMvc.perform(post("/test/api-exception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"conflict\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        ErrorEnvelope conflictEnv = parseEnvelope(conflictResult);
        assertThat(conflictEnv.type()).isEqualTo("https://api.studio.example/errors/conflict");

        MvcResult concurrentResult = mockMvc.perform(post("/test/optimistic-lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn();
        ErrorEnvelope concurrentEnv = parseEnvelope(concurrentResult);
        assertThat(concurrentEnv.type()).isEqualTo("https://api.studio.example/errors/concurrent-modification");

        MvcResult internalResult = mockMvc.perform(post("/test/unhandled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        ErrorEnvelope internalEnv = parseEnvelope(internalResult);
        assertThat(internalEnv.type()).isEqualTo("https://api.studio.example/errors/internal-error");
    }

    // =========================================================================
    // AC-8: Optimistic lock failure → 409 CONCURRENT_MODIFICATION
    // =========================================================================

    @Test
    void testAc8OptimisticLockMapsTo409ConcurrentModification() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/optimistic-lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(409);
        assertThat(env.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    // =========================================================================
    // AC-9: Malformed JSON → 400 MALFORMED_REQUEST
    // =========================================================================

    @Test
    void testAc9MalformedJsonMapsTo400MalformedRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.status()).isEqualTo(400);
        assertThat(env.code()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
        assertThat(env.errors()).isNull();
    }

    // =========================================================================
    // Additional shape assertions: all required fields always present
    // =========================================================================

    @Test
    void testEnvelopeAlwaysContainsAllRequiredFields() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/api-exception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"notPermitted\"}"))
                .andExpect(status().isForbidden())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.type()).isNotNull().startsWith("https://api.studio.example/errors/");
        assertThat(env.title()).isNotNull().isNotBlank();
        assertThat(env.status()).isEqualTo(403);
        assertThat(env.code()).isNotNull();
        assertThat(env.detail()).isNotNull().isNotBlank();
        assertThat(env.instance()).isNotNull();
        assertThat(env.timestamp()).isNotNull();
        assertThat(env.traceId()).isNotNull().isNotBlank();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ErrorEnvelope parseEnvelope(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorEnvelope.class);
    }

    private void assertCommonFields(ErrorEnvelope env, int expectedStatus, ErrorCode expectedCode) {
        assertThat(env.type()).isNotNull().startsWith("https://api.studio.example/errors/");
        assertThat(env.title()).isNotNull().isNotBlank();
        assertThat(env.status()).isEqualTo(expectedStatus);
        assertThat(env.code()).isEqualTo(expectedCode);
        assertThat(env.detail()).isNotNull().isNotBlank();
        assertThat(env.instance()).isNotNull();
        assertThat(env.timestamp()).isNotNull();
        assertThat(env.traceId()).isNotNull().isNotBlank();
    }
}
