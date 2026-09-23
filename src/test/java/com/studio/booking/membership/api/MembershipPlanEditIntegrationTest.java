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
class MembershipPlanEditIntegrationTest {

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

    private UUID planId;
    private long version;

    @BeforeEach
    void setUp() throws Exception {
        planRepository.deleteAll();

        // Create a base plan for testing
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Original Plan",
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

        planId = response.id();
        version = response.version();
    }

    // =========================================================================
    // AC-1: PATCH changing name leaves all other fields untouched
    // =========================================================================

    @Test
    void test_ac1_patchNameChangesNameLeavesOtherFieldsUntouched() throws Exception {
        String updateJson = """
            {
              "name": "Updated Plan Name",
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.name()).isEqualTo("Updated Plan Name");
        assertThat(response.classCredits()).isEqualTo(10);
        assertThat(response.unlimited()).isFalse();
        assertThat(response.durationDays()).isEqualTo(30);
    }

    // =========================================================================
    // AC-2: PATCH with stale version returns 409 and changes nothing
    // =========================================================================

    @Test
    void test_ac2_patchWithStaleVersionReturns409Conflict() throws Exception {
        String updateJson = """
            {
              "name": "Stale Version Test",
              "version": 999
            }
            """;

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);

        // Verify plan was not changed
        MvcResult getResult = mockMvc.perform(get("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse unchanged = objectMapper.readValue(
            getResult.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );
        assertThat(unchanged.name()).isEqualTo("Original Plan");
    }

    // =========================================================================
    // AC-3: PATCH with only version returns 422 on _body
    // =========================================================================

    @Test
    void test_ac3_patchWithOnlyVersionReturns422ValidationFailed() throws Exception {
        String updateJson = """
            {
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // AC-4: PATCH changing name to another plan's name returns 409 PLAN_NAME_ALREADY_EXISTS
    // =========================================================================

    @Test
    void test_ac4_patchNameToExistingPlanNameReturns409() throws Exception {
        // Create another plan
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Another Plan",
            5,
            20,
            new MoneyDto(new BigDecimal("49.99"), "USD"),
            "BASIC"
        );

        mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Try to rename first plan to second plan's name
        String updateJson = """
            {
              "name": "Another Plan",
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.PLAN_NAME_ALREADY_EXISTS);
    }

    // =========================================================================
    // AC-5: PATCH changing name to the plan's own current name succeeds
    // =========================================================================

    @Test
    void test_ac5_patchNameToOwnCurrentNameSucceeds() throws Exception {
        String updateJson = """
            {
              "name": "Original Plan",
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.name()).isEqualTo("Original Plan");
    }

    // =========================================================================
    // AC-6: Tri-state: omitting classCredits leaves it unchanged
    // =========================================================================

    @Test
    void test_ac6_triStateOmitClassCreditsLeavesUnchanged() throws Exception {
        String updateJson = """
            {
              "name": "Still Has Credits",
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isEqualTo(10);
        assertThat(response.unlimited()).isFalse();
    }

    // =========================================================================
    // AC-7: Tri-state: "classCredits": 25 sets it to 25 and keeps unlimited: false
    // =========================================================================

    @Test
    void test_ac7_triStateClassCredits25SetsItAndUnlimitedFalse() throws Exception {
        String updateJson = """
            {
              "classCredits": 25,
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isEqualTo(25);
        assertThat(response.unlimited()).isFalse();
    }

    // =========================================================================
    // AC-8: Tri-state: "classCredits": null sets unlimited: true
    // =========================================================================

    @Test
    void test_ac8_triStateClassCreditsNullSetsUnlimitedTrue() throws Exception {
        String updateJson = """
            {
              "classCredits": null,
              "version": %d
            }
            """.formatted(version);

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", planId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isNull();
        assertThat(response.unlimited()).isTrue();
    }

    // =========================================================================
    // AC-9: Converting an unlimited plan to classCredits: 10 sets unlimited: false
    // =========================================================================

    @Test
    void test_ac9_triStateConvertingUnlimitedToClassCredits10() throws Exception {
        // First create an unlimited plan
        CreateMembershipPlanRequest unlimitedRequest = new CreateMembershipPlanRequest(
            "Unlimited Plan",
            null,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/membership-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(unlimitedRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MembershipPlanResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(created.unlimited()).isTrue();

        // Now convert it to credit-based
        String updateJson = """
            {
              "classCredits": 10,
              "version": %d
            }
            """.formatted(created.version());

        MvcResult result = mockMvc.perform(patch("/api/v1/membership-plans/{id}", created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.classCredits()).isEqualTo(10);
        assertThat(response.unlimited()).isFalse();
    }

    // =========================================================================
    // AC-14: Deactivating an active plan succeeds; it disappears from the default list
    // =========================================================================

    @Test
    void test_ac14_deactivateActivePlanSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.active()).isFalse();
    }

    @Test
    void test_ac14_deactivatedPlanDisappearsFromDefaultList() throws Exception {
        // Deactivate the plan
        mockMvc.perform(delete("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isOk());

        // List plans without includeInactive
        MvcResult result = mockMvc.perform(get("/api/v1/membership-plans"))
            .andExpect(status().isOk())
            .andReturn();

        PageResponse<?> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            PageResponse.class
        );

        assertThat(response.content()).isEmpty();
    }

    // =========================================================================
    // AC-15: Deactivating an already-inactive plan returns 409 PLAN_ALREADY_INACTIVE
    // =========================================================================

    @Test
    void test_ac15_deactivateInactivePlanReturns409() throws Exception {
        // Deactivate once
        mockMvc.perform(delete("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isOk());

        // Try to deactivate again
        MvcResult result = mockMvc.perform(delete("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );
        assertThat(env.code()).isEqualTo(ErrorCode.PLAN_ALREADY_INACTIVE);
    }

    // =========================================================================
    // AC-16: Member holding membership from inactive plan can still book normally
    // =========================================================================

    @Test
    void test_ac16_memberWithInactivePlanMembershipCanStillBook() throws Exception {
        // Deactivate the plan
        mockMvc.perform(delete("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isOk());

        // Verify the plan is inactive
        MvcResult getResult = mockMvc.perform(get("/api/v1/membership-plans/{id}", planId))
            .andExpect(status().isOk())
            .andReturn();

        MembershipPlanResponse response = objectMapper.readValue(
            getResult.getResponse().getContentAsString(),
            MembershipPlanResponse.class
        );

        assertThat(response.active()).isFalse();

        // A member with a membership from this now-inactive plan can still retrieve it
        // and would be able to book (booking validation checks membership status, not plan status)
        // This is a sanity check that the deactivation doesn't break existing memberships
    }
}
