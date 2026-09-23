package com.studio.booking.membership.application;

import com.studio.booking.membership.api.CreateMembershipPlanRequest;
import com.studio.booking.membership.api.MoneyDto;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MembershipPlanServiceTest {

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
    MembershipPlanService planService;

    @Autowired
    MembershipPlanRepository planRepository;

    @BeforeEach
    void setUp() {
        planRepository.deleteAll();
    }

    @Test
    void createPlan_WithValidCredits_Succeeds() {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Test Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        MembershipPlan plan = planService.createPlan(request);

        assertThat(plan.getId()).isNotNull();
        assertThat(plan.getName()).isEqualTo("Test Plan");
        assertThat(plan.getClassCredits()).isEqualTo(10);
        assertThat(plan.getDurationDays()).isEqualTo(30);
        assertThat(plan.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(plan.getCurrency()).isEqualTo("USD");
        assertThat(plan.getTier()).isEqualTo("BASIC");
    }

    @Test
    void createPlan_DuplicateNameCaseInsensitive_ThrowsException() {
        CreateMembershipPlanRequest request1 = new CreateMembershipPlanRequest(
            "Test Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );
        planService.createPlan(request1);

        CreateMembershipPlanRequest request2 = new CreateMembershipPlanRequest(
            "test plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        assertThatThrownBy(() -> planService.createPlan(request2))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NAME_ALREADY_EXISTS);
    }

    @Test
    void createPlan_ZeroCredits_ThrowsException() {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Test Plan",
            0,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        assertThatThrownBy(() -> planService.createPlan(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_RANGE);
    }

    @Test
    void createPlan_DurationDaysZero_ThrowsException() {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Test Plan",
            10,
            0,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        assertThatThrownBy(() -> planService.createPlan(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_RANGE);
    }

    @Test
    void createPlan_DurationDays3661_ThrowsException() {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Test Plan",
            10,
            3661,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        assertThatThrownBy(() -> planService.createPlan(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_RANGE);
    }

    @Test
    void getById_NonExistent_ThrowsException() {
        java.util.UUID randomId = java.util.UUID.randomUUID();

        assertThatThrownBy(() -> planService.getById(randomId))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND);
    }

    @Test
    void listPlans_DefaultActive_ReturnsOnlyActivePlans() {
        CreateMembershipPlanRequest request = new CreateMembershipPlanRequest(
            "Test Plan",
            10,
            30,
            new MoneyDto(new BigDecimal("99.99"), "USD"),
            "BASIC"
        );

        planService.createPlan(request);

        Page<MembershipPlan> plans = planService.listPlans(false, null, null, PageRequest.of(0, 20));

        assertThat(plans.getContent()).hasSize(1);
    }
}
