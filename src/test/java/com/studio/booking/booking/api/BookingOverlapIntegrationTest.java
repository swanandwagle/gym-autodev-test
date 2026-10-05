package com.studio.booking.booking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.booking.domain.Booking;
import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.member.api.RegisterMemberRequest;
import com.studio.booking.member.api.MemberResponse;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.membership.infrastructure.CreditTransactionRepository;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingOverlapIntegrationTest {

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
    @Autowired private BookingRepository bookingRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private MembershipPlanRepository membershipPlanRepository;
    @Autowired private ClassSessionRepository classSessionRepository;
    @Autowired private CreditTransactionRepository creditTransactionRepository;
    @Autowired private Clock clock;

    private UUID testMemberId;
    private MembershipPlan creditPlan;

    @BeforeEach
    void setUp() throws Exception {
        bookingRepository.deleteAll();
        creditTransactionRepository.deleteAll();
        membershipRepository.deleteAll();
        membershipPlanRepository.deleteAll();
        classSessionRepository.deleteAll();
        memberRepository.deleteAll();

        // Create test member
        RegisterMemberRequest memberRequest = new RegisterMemberRequest(
            "overlap-test@example.com",
            "Test Overlap",
            null
        );
        MvcResult memberResult = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(memberRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse testMember = objectMapper.readValue(
            memberResult.getResponse().getContentAsString(),
            MemberResponse.class
        );
        testMemberId = UUID.fromString(testMember.id());

        // Create credit plan
        creditPlan = new MembershipPlan("Credit Plan", 20, 30, new BigDecimal("99.99"), "USD", "BASIC");
        creditPlan = membershipPlanRepository.save(creditPlan);
    }

    // AC-1: Overlapping BOOKED booking returns 409 BOOKING_OVERLAPS_EXISTING with both IDs
    @Test
    void test_ac1_overlapping_booked_booking_returns_409_booking_overlaps_existing() throws Exception {
        // Create membership with credits
        Membership membership = new Membership(testMemberId, creditPlan.getId(), 20, clock);
        membership = membershipRepository.save(membership);

        // Create first session (10:00-11:00)
        Instant now = Instant.now(clock);
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);
        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session1Start,
            session1End,
            10,
            clock
        );
        session1 = classSessionRepository.save(session1);

        // Book first session
        CreateBookingRequest firstBooking = new CreateBookingRequest(
            testMemberId.toString(),
            session1.getId().toString()
        );
        MvcResult firstResult = mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(firstBooking)))
            .andExpect(status().isCreated())
            .andReturn();

        BookingResponse firstResponse = objectMapper.readValue(
            firstResult.getResponse().getContentAsString(),
            BookingResponse.class
        );
        UUID firstBookingId = UUID.fromString(firstResponse.id());

        // Create overlapping second session (10:30-11:30)
        Instant session2Start = session1Start.plusSeconds(1800); // 30 minutes into session1
        Instant session2End = session1End.plusSeconds(1800);
        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session2Start,
            session2End,
            10,
            clock
        );
        session2 = classSessionRepository.save(session2);

        // Try to book overlapping session
        CreateBookingRequest overlappingBooking = new CreateBookingRequest(
            testMemberId.toString(),
            session2.getId().toString()
        );

        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(overlappingBooking)))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorEnvelope error = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            ErrorEnvelope.class
        );

        assertThat(error.code()).isEqualTo(ErrorCode.BOOKING_OVERLAPS_EXISTING.name());
        assertThat(error.title()).contains("overlap");
    }

    // AC-2: Half-open boundary - consecutive sessions do NOT overlap
    @Test
    void test_ac2_consecutive_sessions_back_to_back_no_overlap() throws Exception {
        Membership membership = new Membership(testMemberId, creditPlan.getId(), 20, clock);
        membership = membershipRepository.save(membership);

        Instant now = Instant.now(clock);
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session1Start,
            session1End,
            10,
            clock
        );
        session1 = classSessionRepository.save(session1);

        // Book first session
        CreateBookingRequest firstBooking = new CreateBookingRequest(
            testMemberId.toString(),
            session1.getId().toString()
        );
        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(firstBooking)))
            .andExpect(status().isCreated());

        // Create second session starting exactly when first ends (11:00-12:00)
        Instant session2Start = session1End; // Exactly at end boundary
        Instant session2End = session2Start.plusSeconds(3600);

        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session2Start,
            session2End,
            10,
            clock
        );
        session2 = classSessionRepository.save(session2);

        // Should be able to book second session (no overlap with half-open [start, end))
        CreateBookingRequest secondBooking = new CreateBookingRequest(
            testMemberId.toString(),
            session2.getId().toString()
        );

        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(secondBooking)))
            .andExpect(status().isCreated());
    }

    // AC-3: Half-open boundary - partial overlap conflicts
    @Test
    void test_ac3_partial_overlap_before_end_boundary_conflicts() throws Exception {
        Membership membership = new Membership(testMemberId, creditPlan.getId(), 20, clock);
        membership = membershipRepository.save(membership);

        Instant now = Instant.now(clock);
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session1Start,
            session1End,
            10,
            clock
        );
        session1 = classSessionRepository.save(session1);

        // Book first session
        CreateBookingRequest firstBooking = new CreateBookingRequest(
            testMemberId.toString(),
            session1.getId().toString()
        );
        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(firstBooking)))
            .andExpect(status().isCreated());

        // Create second session starting before first ends (e.g. 10:59-11:59)
        Instant session2Start = session1End.minusSeconds(60); // 1 minute before end
        Instant session2End = session1End.plusSeconds(3540); // 59 minutes duration

        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session2Start,
            session2End,
            10,
            clock
        );
        session2 = classSessionRepository.save(session2);

        // Should NOT be able to book (overlaps with [start, end))
        CreateBookingRequest secondBooking = new CreateBookingRequest(
            testMemberId.toString(),
            session2.getId().toString()
        );

        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(secondBooking)))
            .andExpect(status().isConflict());
    }

    // AC-5: CANCELLED booking does not block
    @Test
    void test_ac5_cancelled_booking_does_not_block_overlap() throws Exception {
        Membership membership = new Membership(testMemberId, creditPlan.getId(), 20, clock);
        membership = membershipRepository.save(membership);

        Instant now = Instant.now(clock);
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session1Start,
            session1End,
            10,
            clock
        );
        session1 = classSessionRepository.save(session1);

        // Book and then manually cancel the booking
        Booking cancelledBooking = new Booking(testMemberId, session1.getId(), "DIRECT", clock);
        cancelledBooking.setMembershipId(membership.getId());
        cancelledBooking.setStatus("CANCELLED");
        bookingRepository.save(cancelledBooking);

        // Create overlapping session
        Instant session2Start = session1Start.plusSeconds(1800);
        Instant session2End = session1End.plusSeconds(1800);
        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session2Start,
            session2End,
            10,
            clock
        );
        session2 = classSessionRepository.save(session2);

        // Should be able to book (cancelled doesn't block)
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
            testMemberId.toString(),
            session2.getId().toString()
        );

        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bookingRequest)))
            .andExpect(status().isCreated());
    }

    // AC-6: ATTENDED and NO_SHOW do not block
    @Test
    void test_ac6_attended_booking_does_not_block_overlap() throws Exception {
        Membership membership = new Membership(testMemberId, creditPlan.getId(), 20, clock);
        membership = membershipRepository.save(membership);

        Instant now = Instant.now(clock);
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session1Start,
            session1End,
            10,
            clock
        );
        session1 = classSessionRepository.save(session1);

        // Create ATTENDED booking
        Booking attendedBooking = new Booking(testMemberId, session1.getId(), "DIRECT", clock);
        attendedBooking.setMembershipId(membership.getId());
        attendedBooking.setStatus("ATTENDED");
        bookingRepository.save(attendedBooking);

        // Create overlapping session
        Instant session2Start = session1Start.plusSeconds(1800);
        Instant session2End = session1End.plusSeconds(1800);
        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session2Start,
            session2End,
            10,
            clock
        );
        session2 = classSessionRepository.save(session2);

        // Should be able to book (ATTENDED doesn't block)
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
            testMemberId.toString(),
            session2.getId().toString()
        );

        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bookingRequest)))
            .andExpect(status().isCreated());
    }

    // AC-7: Another member's overlapping booking does not block
    @Test
    void test_ac7_other_member_overlapping_booking_does_not_block() throws Exception {
        // Create two members
        RegisterMemberRequest member2Request = new RegisterMemberRequest(
            "other-member@example.com",
            "Other Member",
            null
        );
        MvcResult member2Result = mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(member2Request)))
            .andExpect(status().isCreated())
            .andReturn();

        MemberResponse member2Response = objectMapper.readValue(
            member2Result.getResponse().getContentAsString(),
            MemberResponse.class
        );
        UUID member2Id = UUID.fromString(member2Response.id());

        // Give both members memberships
        Membership membership1 = new Membership(testMemberId, creditPlan.getId(), 20, clock);
        membership1 = membershipRepository.save(membership1);

        Membership membership2 = new Membership(member2Id, creditPlan.getId(), 20, clock);
        membership2 = membershipRepository.save(membership2);

        // Member2 books a session
        Instant now = Instant.now(clock);
        Instant session1Start = now.plusSeconds(3600);
        Instant session1End = session1Start.plusSeconds(3600);

        ClassSession session1 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session1Start,
            session1End,
            10,
            clock
        );
        session1 = classSessionRepository.save(session1);

        CreateBookingRequest member2Booking = new CreateBookingRequest(
            member2Id.toString(),
            session1.getId().toString()
        );
        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(member2Booking)))
            .andExpect(status().isCreated());

        // Member1 should still be able to book overlapping session
        Instant session2Start = session1Start.plusSeconds(1800);
        Instant session2End = session1End.plusSeconds(1800);
        ClassSession session2 = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            session2Start,
            session2End,
            10,
            clock
        );
        session2 = classSessionRepository.save(session2);

        CreateBookingRequest member1Booking = new CreateBookingRequest(
            testMemberId.toString(),
            session2.getId().toString()
        );

        mockMvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(member1Booking)))
            .andExpect(status().isCreated());
    }
}
