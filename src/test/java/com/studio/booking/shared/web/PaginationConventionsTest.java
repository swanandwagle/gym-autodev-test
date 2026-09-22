package com.studio.booking.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.error.FieldError;
import com.studio.booking.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for GYM-22: Pagination, sorting and shared query parameter conventions.
 * Covers AC-1 through AC-10.
 */
@WebMvcTest(
        controllers = PaginationConventionsTest.TestController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@Import({
        GlobalExceptionHandler.class,
        com.studio.booking.shared.web.CorrelationFilter.class,
        com.studio.booking.shared.web.StrictJsonConfig.class,
        PageParamsValidator.class,
        SortValidator.class,
        DateRangeValidator.class
})
@TestPropertySource(properties = "studio.api.base-url=https://api.studio.example")
class PaginationConventionsTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // =========================================================================
    // Test controller — three endpoint categories:
    //   /test/paginated             : sortable list
    //   /test/fixed-order           : fixed-order list (no sort)
    //   /test/date-range            : date-range filtered list
    //   /test/enum-filter           : multi-value enum filter
    // =========================================================================

    @RestController
    @RequestMapping("/test")
    static class TestController {

        private final PageParamsValidator pageValidator;
        private final SortValidator sortValidator;
        private final DateRangeValidator dateRangeValidator;

        TestController(PageParamsValidator pageValidator,
                       SortValidator sortValidator,
                       DateRangeValidator dateRangeValidator) {
            this.pageValidator = pageValidator;
            this.sortValidator = sortValidator;
            this.dateRangeValidator = dateRangeValidator;
        }

        /** Sortable paginated endpoint — allowed sorts: createdAt, name */
        @GetMapping("/paginated")
        PageResponse<String> paginated(SortablePageParams params) {
            pageValidator.validate(params);
            sortValidator.validate(params, new String[]{"createdAt", "name"});
            // Build synthetic result based on requested page/size for boundary tests
            int size = params.getSize();
            int pageNum = params.getPage();
            long totalElements = (long) params.getSize() * 2 + 1; // always 2+ pages worth
            return PageResponse.of(List.of("item"), pageNum, size, totalElements);
        }

        /** Sortable endpoint with controlled total for boundary tests */
        @GetMapping("/boundary")
        PageResponse<String> boundary(
                SortablePageParams params,
                @RequestParam(defaultValue = "0") long totalElements) {
            pageValidator.validate(params);
            int size = params.getSize();
            int pageNum = params.getPage();
            List<String> items = totalElements == 0 ? List.of() : List.of("item");
            return PageResponse.of(items, pageNum, size, totalElements);
        }

        /** Fixed-order endpoint — sort is not permitted */
        @GetMapping("/fixed-order")
        PageResponse<String> fixedOrder(SortablePageParams params) {
            pageValidator.validate(params);
            sortValidator.validateFixedOrder(params);
            return PageResponse.of(List.of("item"), params.getPage(), params.getSize(), 1L);
        }

        /** Date-range endpoint with 31-day cap */
        @GetMapping("/date-range")
        PageResponse<String> dateRange(
                PageParams pageParams,
                DateRangeParams dateRange) {
            pageValidator.validate(pageParams);
            dateRangeValidator.validate(dateRange, 31);
            return PageResponse.of(List.of("item"), pageParams.getPage(), pageParams.getSize(), 1L);
        }

        /** Multi-value enum filter endpoint */
        @GetMapping("/enum-filter")
        PageResponse<String> enumFilter(
                PageParams pageParams,
                @RequestParam(required = false) List<String> status) {
            pageValidator.validate(pageParams);
            if (status != null) {
                List<String> allowed = List.of("ACTIVE", "INACTIVE", "SUSPENDED");
                List<String> invalid = status.stream()
                        .filter(s -> !allowed.contains(s))
                        .toList();
                if (!invalid.isEmpty()) {
                    String permitted = String.join(", ", allowed);
                    throw com.studio.booking.shared.error.ApiException.validationFailed(
                            "Request validation failed. See errors.",
                            List.of(FieldError.of(
                                    "status",
                                    "INVALID_ENUM",
                                    "Invalid status value(s). Permitted: " + permitted,
                                    String.join(", ", invalid))));
                }
            }
            return PageResponse.of(List.of("item"), pageParams.getPage(), pageParams.getSize(), 1L);
        }
    }

    // =========================================================================
    // AC-1: Defaults apply when parameters are omitted
    // =========================================================================

    @Test
    void testAc1DefaultsApplyWhenParamsOmitted() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/paginated"))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(PageResponse.class, String.class));

        assertThat(response.page().number()).isEqualTo(0);
        assertThat(response.page().size()).isEqualTo(PageParams.DEFAULT_SIZE);
    }

    // =========================================================================
    // AC-2: size=0, size=101, page=-1 each rejected with OUT_OF_RANGE
    // =========================================================================

    @Test
    void testAc2SizeZeroRejectedWithOutOfRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/paginated?size=0"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "size", "OUT_OF_RANGE");
    }

    @Test
    void testAc2Size101RejectedWithOutOfRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/paginated?size=101"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "size", "OUT_OF_RANGE");
    }

    @Test
    void testAc2PageMinusOneRejectedWithOutOfRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/paginated?page=-1"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "page", "OUT_OF_RANGE");
    }

    // =========================================================================
    // AC-3: Sort field outside allow-list → INVALID_ENUM with permitted fields in message
    // =========================================================================

    @Test
    void testAc3SortFieldNotInAllowListRejectedWithInvalidEnum() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/paginated?sort=invalidField,asc"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();

        FieldError sortError = env.errors().stream()
                .filter(fe -> "sort".equals(fe.field()))
                .findFirst()
                .orElse(null);
        assertThat(sortError).isNotNull();
        assertThat(sortError.code()).isEqualTo("INVALID_ENUM");
        // Message must list permitted fields
        assertThat(sortError.message()).contains("createdAt");
        assertThat(sortError.message()).contains("name");
    }

    @Test
    void testAc3AllowedSortFieldAccepted() throws Exception {
        mockMvc.perform(get("/test/paginated?sort=createdAt,desc"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // AC-4: Sort on fixed-order endpoint is rejected
    // =========================================================================

    @Test
    void testAc4SortOnFixedOrderEndpointRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/fixed-order?sort=createdAt,asc"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();
        boolean hasSortError = env.errors().stream().anyMatch(fe -> "sort".equals(fe.field()));
        assertThat(hasSortError).isTrue();
    }

    @Test
    void testAc4FixedOrderEndpointWithoutSortSucceeds() throws Exception {
        mockMvc.perform(get("/test/fixed-order"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // AC-5: to equal to or before from → INVALID_RANGE
    // =========================================================================

    @Test
    void testAc5ToEqualFromReturnsInvalidRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/date-range?from=2026-01-15&to=2026-01-15"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "to", "INVALID_RANGE");
    }

    @Test
    void testAc5ToBeforeFromReturnsInvalidRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/date-range?from=2026-02-01&to=2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "to", "INVALID_RANGE");
    }

    // =========================================================================
    // AC-6: Span exceeding endpoint cap → OUT_OF_RANGE on to
    // =========================================================================

    @Test
    void testAc6SpanExceedingCapReturnsOutOfRangeOnTo() throws Exception {
        // cap is 31 days; from=2026-01-01, to=2026-03-01 = 59 days
        MvcResult result = mockMvc.perform(get("/test/date-range?from=2026-01-01&to=2026-03-01"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "to", "OUT_OF_RANGE");
    }

    @Test
    void testAc6SpanAtCapAccepted() throws Exception {
        // cap is 31 days; from=2026-01-01, to=2026-02-01 = exactly 31 days
        mockMvc.perform(get("/test/date-range?from=2026-01-01&to=2026-02-01"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // AC-7: date combined with from or to → INVALID_RANGE
    // =========================================================================

    @Test
    void testAc7DateCombinedWithFromReturnsInvalidRange() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/test/date-range?date=2026-01-15&from=2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "date", "INVALID_RANGE");
    }

    @Test
    void testAc7DateCombinedWithToReturnsInvalidRange() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/test/date-range?date=2026-01-15&to=2026-02-01"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "date", "INVALID_RANGE");
    }

    @Test
    void testAc7DateAloneIsAccepted() throws Exception {
        mockMvc.perform(get("/test/date-range?date=2026-01-15"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // AC-8: Multi-value enum with one bad member → whole parameter rejected
    // =========================================================================

    @Test
    void testAc8MultiValueEnumWithOneBadMemberRejectsAll() throws Exception {
        // ACTIVE is valid, UNKNOWN is not — entire param must be rejected
        MvcResult result = mockMvc.perform(
                        get("/test/enum-filter?status=ACTIVE&status=UNKNOWN"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = parseEnvelope(result);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertFieldCode(env, "status", "INVALID_ENUM");
    }

    @Test
    void testAc8AllValidEnumValuesAccepted() throws Exception {
        mockMvc.perform(get("/test/enum-filter?status=ACTIVE&status=INACTIVE"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // AC-9: Page envelope shape is identical across every paginated endpoint
    // =========================================================================

    @Test
    void testAc9PageEnvelopeShapeOnPaginatedEndpoint() throws Exception {
        assertPageEnvelopeShape("/test/paginated");
    }

    @Test
    void testAc9PageEnvelopeShapeOnFixedOrderEndpoint() throws Exception {
        assertPageEnvelopeShape("/test/fixed-order");
    }

    @Test
    void testAc9PageEnvelopeShapeOnDateRangeEndpoint() throws Exception {
        assertPageEnvelopeShape("/test/date-range");
    }

    // =========================================================================
    // AC-10: totalPages correct at boundaries
    // =========================================================================

    @Test
    void testAc10TotalPagesZeroResults() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/boundary?size=20&totalElements=0"))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> response = parsePageResponse(result);
        assertThat(response.page().totalElements()).isEqualTo(0);
        assertThat(response.page().totalPages()).isEqualTo(0);
    }

    @Test
    void testAc10TotalPagesExactlyOnePage() throws Exception {
        // 20 results, size=20 → exactly 1 page
        MvcResult result = mockMvc.perform(get("/test/boundary?size=20&totalElements=20"))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> response = parsePageResponse(result);
        assertThat(response.page().totalElements()).isEqualTo(20);
        assertThat(response.page().totalPages()).isEqualTo(1);
    }

    @Test
    void testAc10TotalPagesOneResultOverPageBoundary() throws Exception {
        // 21 results, size=20 → 2 pages
        MvcResult result = mockMvc.perform(get("/test/boundary?size=20&totalElements=21"))
                .andExpect(status().isOk())
                .andReturn();

        PageResponse<?> response = parsePageResponse(result);
        assertThat(response.page().totalElements()).isEqualTo(21);
        assertThat(response.page().totalPages()).isEqualTo(2);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void assertPageEnvelopeShape(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Must have top-level content array and page object with all four fields
        assertThat(body).contains("\"content\"");
        assertThat(body).contains("\"page\"");
        assertThat(body).contains("\"number\"");
        assertThat(body).contains("\"size\"");
        assertThat(body).contains("\"totalElements\"");
        assertThat(body).contains("\"totalPages\"");
    }

    private ErrorEnvelope parseEnvelope(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorEnvelope.class);
    }

    @SuppressWarnings("unchecked")
    private PageResponse<?> parsePageResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(PageResponse.class, String.class));
    }

    private void assertFieldCode(ErrorEnvelope env, String field, String expectedCode) {
        boolean found = env.errors() != null && env.errors().stream()
                .anyMatch(fe -> field.equals(fe.field()) && expectedCode.equals(fe.code()));
        assertThat(found)
                .as("Expected field '%s' with code '%s' in errors: %s", field, expectedCode, env.errors())
                .isTrue();
    }
}
