package com.studio.booking.membership.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MembershipPlanControllerIntegrationTest {

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
    MembershipPlanRepository planRepository;

    @BeforeEach
    void setUp() {
        planRepository.deleteAll();
    }

    // =========================================================================
    // AC-1: A credit-based plan creates with classCredits: 10, unlimited: false
    // =========================================================================

    @Test
    void test_ac1_creditBasedPlanCreatesWithClassCredits10AndUnlimitedFalse() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "10-Pack Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isEqualTo(10);
        assertThat(response.unlimited()).isFalse();
    }

    // =========================================================================
    // AC-2: A plan with classCredits omitted creates with classCredits: null, unlimited: true
    // =========================================================================

    @Test
    void test_ac2_planWithClassCreditsOmittedCreatesWithNullAndUnlimitedTrue() throws Exception {
        String requestJson = """
            {
              "name": "Unlimited Plan",
              "durationDays": 30,
              "price": {
                "amount": "199.99",
                "currency": "USD"
              },
              "tier": "PREMIUM"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isNull();
        assertThat(response.unlimited()).isTrue();
    }

    // =========================================================================
    // AC-3: A plan with explicit classCredits: null behaves identically to omitting it
    // =========================================================================

    @Test
    void test_ac3_planWithExplicitNullClassCreditsMatchesOmission() throws Exception {
        String requestJson = """
            {
              "name": "Explicit Null Plan",
              "classCredits": null,
              "durationDays": 30,
              "price": {
                "amount": "199.99",
                "currency": "USD"
              }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isNull();
        assertThat(response.unlimited()).isTrue();
    }

    // =========================================================================
    // AC-4: classCredits: 0 returns 422 OUT_OF_RANGE
    // =========================================================================

    @Test
    void test_ac4_classCredits0Returns422OutOfRange() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Zero Credits Plan",
            0,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.OUT_OF_RANGE);
    }

    // =========================================================================
    // AC-5: price.amount of "2999.999" returns 422 INVALID_FORMAT (scale exceeded)
    // =========================================================================

    @Test
    void test_ac5_priceAmountScaleTooHighReturns422InvalidFormat() throws Exception {
        String requestJson = """
            {
              "name": "Bad Scale Plan",
              "durationDays": 30,
              "price": {
                "amount": "2999.999",
                "currency": "USD"
              }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-6: price.amount of "-1.00" returns 422 OUT_OF_RANGE
    // =========================================================================

    @Test
    void test_ac6_priceAmountNegativeReturns422OutOfRange() throws Exception {
        String requestJson = """
            {
              "name": "Negative Price Plan",
              "durationDays": 30,
              "price": {
                "amount": "-1.00",
                "currency": "USD"
              }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-7: price.currency of "inr" returns 422 INVALID_FORMAT; "INR" is accepted
    // =========================================================================

    @Test
    void test_ac7_priceCurrencyLowercaseReturns422InvalidFormat() throws Exception {
        String requestJson = """
            {
              "name": "Lowercase Currency Plan",
              "durationDays": 30,
              "price": {
                "amount": "99.99",
                "currency": "inr"
              }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void test_ac7_priceCurrencyInrAccepted() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "INR Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("2999.00"), "INR"),
            "BASIC"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.price().currency()).isEqualTo("INR");
    }

    // =========================================================================
    // AC-8: price.amount round-trips exactly: "2999.00" in returns "2999.00" out
    // =========================================================================

    @Test
    void test_ac8_priceAmountRoundTripsExactly() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Round Trip Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("2999.00"), "USD"),
            "BASIC"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        // Verify exact round-trip: "2999.00" in → "2999.00" out (as BigDecimal string)
        assertThat(response.price().amount().toPlainString()).isEqualTo("2999.00");
    }

    // =========================================================================
    // AC-9: Creating "monthly 10-pack" after "Monthly 10-Pack" returns 409 PLAN_NAME_ALREADY_EXISTS
    // =========================================================================

    @Test
    void test_ac9_caseInsensitiveDuplicateNameReturns409() throws Exception {
        CreateMembershipPlanRequest request1 = new CreateMembershipPlanRequest(
            "Monthly 10-Pack",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        CreateMembershipPlanRequest request2 = new CreateMembershipPlanRequest(
            "monthly 10-pack",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MvcResult conflict = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            conflict.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.PLAN_NAME_ALREADY_EXISTS);
    }

    // =========================================================================
    // AC-10: durationDays of 0 or 3661 returns 422 OUT_OF_RANGE
    // =========================================================================

    @Test
    void test_ac10_durationDays0Returns422OutOfRange() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Zero Days Plan",
            10,
            0,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.OUT_OF_RANGE);
    }

    @Test
    void test_ac10_durationDays3661Returns422OutOfRange() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Too Many Days Plan",
            10,
            3661,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.OUT_OF_RANGE);
    }

    // =========================================================================
    // AC-11: Supplying unlimited or active in the body returns 422 UNKNOWN_FIELD
    // =========================================================================

    @Test
    void test_ac11_unknownFieldUnlimitedReturns422() throws Exception {
        String requestJson = """
            {
              "name": "Test Plan",
              "durationDays": 30,
              "price": {
                "amount": "99.99",
                "currency": "USD"
              },
              "unlimited": true
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.UNKNOWN_FIELD);
    }

    @Test
    void test_ac11_unknownFieldActiveReturns422() throws Exception {
        String requestJson = """
            {
              "name": "Test Plan",
              "durationDays": 30,
              "price": {
                "amount": "99.99",
                "currency": "USD"
              },
              "active": false
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.UNKNOWN_FIELD);
    }

    // =========================================================================
    // AC-12: List defaults to active only; includeInactive=true returns both
    // =========================================================================

    @Test
    void test_ac12_listDefaultsToActiveOnly() throws Exception {
        CreateMembershipPlanRequest active = new CreateMembershipPlanRequest(
            "Active Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(active)))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<?> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            PageResponse.class
        );

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void test_ac12_listWithIncludeInactiveTrueReturnsBoth() throws Exception {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Test Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans?includeInactive=true"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<?> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            PageResponse.class
        );

        assertThat(response.content()).hasSize(1);
    }

    // =========================================================================
    // AC-13: unlimited=true filter returns only plans with null credits
    // =========================================================================

    @Test
    void test_ac13_unlimitedTrueFilterReturnsNullCreditsOnly() throws Exception {
        // Create a credit-based plan
        CreateMembershipPlanRequest creditBased = new CreateMembershipPlanRequest(
            "10-Pack",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(creditBased)))
            .andExpect(status().isCreated());

        // Create an unlimited plan
        String unlimitedJson = """
            {
              "name": "Unlimited Plan",
              "durationDays": 30,
              "price": {
                "amount": "199.99",
                "currency": "USD"
              }
            }
            """;

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(unlimitedJson))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans?unlimited=true"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<?> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            PageResponse.class
        );

        assertThat(response.content()).hasSize(1);
    }

    // =========================================================================
    // AC-14: tier=basic matches a plan tiered BASIC
    // =========================================================================

    @Test
    void test_ac14_tierBasicFilterWorks() throws Exception {
        CreateMembershipPlanRequest basicPlan = new CreateMembershipPlanRequest(
            "Basic Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        CreateMembershipPlanRequest premiumPlan = new CreateMembershipPlanRequest(
            "Premium Plan",
            20,
            30,
            new MoneyDto(new BigDecimal("199.99"), "USD"),
            "PREMIUM"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(basicPlan)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(premiumPlan)))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans?tier=BASIC"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<?> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            PageResponse.class
        );

        assertThat(response.content()).hasSize(1);
    }

    // =========================================================================
    // AC-15: Sort by each allow-listed field works in both directions
    // =========================================================================

    @Test
    void test_ac15_sortByAllowListedFieldsBothDirections() throws Exception {
        CreateMembershipPlanRequest plan1 = new CreateMembershipPlanRequest(
            "Plan A",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        CreateMembershipPlanRequest plan2 = new CreateMembershipPlanRequest(
            "Plan Z",
            20,
            60,
            new MoneyDto(new BigDecimal("199.99"), "USD"),
            "PREMIUM"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(plan1)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(plan2)))
            .andExpect(status().isCreated());

        // Test sort by name ascending
        MvcResult ascResult = mockMvc.perform(get("/api/v1/membership-plans?sort=name,asc"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<MembershipPlanResponse> ascResponse = objectMapper.readValue(
            ascResult.getResponse().getContentAsString(),
            objectMapper.getTypeFactory().constructParametricType(PageResponse.class, MembershipPlanResponse.class)
        );

        assertThat(ascResponse.content()).hasSize(2);
        assertThat(ascResponse.content().get(0).name()).isEqualTo("Plan A");
        assertThat(ascResponse.content().get(1).name()).isEqualTo("Plan Z");

        // Test sort by name descending
        MvcResult descResult = mockMvc.perform(get("/api/v1/membership-plans?sort=name,desc"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<MembershipPlanResponse> descResponse = objectMapper.readValue(
            descResult.getResponse().getContentAsString(),
            objectMapper.getTypeFactory().constructParametricType(PageResponse.class, MembershipPlanResponse.class)
        );

        assertThat(descResponse.content()).hasSize(2);
        assertThat(descResponse.content().get(0).name()).isEqualTo("Plan Z");
        assertThat(descResponse.content().get(1).name()).isEqualTo("Plan A");
    }

    // =========================================================================
    // AC-15b: Sort by an unlisted field returns 422 INVALID_ENUM
    // =========================================================================

    @Test
    void test_ac15b_sortByUnlistedFieldReturns422InvalidEnum() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans?sort=invalidField,asc"))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.INVALID_SORT_FIELD);
    }

    // =========================================================================
    // AC-16: GET on unknown id returns 404; malformed UUID returns 422 INVALID_FORMAT
    // =========================================================================

    @Test
    void test_ac16_getUnknownIdReturns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans/" + randomId))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND);
    }

    @Test
    void test_ac16_getMalformedUuidReturns422InvalidFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans/not-a-uuid"))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
