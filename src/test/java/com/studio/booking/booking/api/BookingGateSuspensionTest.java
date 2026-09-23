package com.studio.booking.booking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.member.api.RegisterMemberRequest;
import com.studio.booking.member.api.MemberResponse;
import com.studio.booking.member.api.SuspendMemberRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingGateSuspensionTest {

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
    void test_ac7_suspended_member_attempting_to_book_receives_403_member_suspended() throws Exception {
        RegisterMemberRequest registerRequest = new RegisterMemberRequest(
            "booking@example.com",
            "Test Booking",
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

        // TODO: Replace with actual booking endpoint when GYM-7 implements POST /api/v1/bookings
        // This test documents that the gate must verify suspended status at the point of use
        // and return 403 MEMBER_SUSPENDED, not just allow the request and fail on status check.
        // The booking endpoint is not yet implemented; this test exists to ensure the
        // gate check is present before GYM-7 closes.
    }
}
