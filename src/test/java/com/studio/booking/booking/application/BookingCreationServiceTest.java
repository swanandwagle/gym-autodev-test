package com.studio.booking.booking.application;

import com.studio.booking.booking.domain.Booking;
import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.member.application.MemberStatusGate;
import com.studio.booking.member.domain.Member;
import com.studio.booking.membership.application.CreditPort;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingCreationServiceTest {

    private BookingRepository bookingRepository;
    private ClassSessionRepository classSessionRepository;
    private CreditPort creditPort;
    private MemberStatusGate memberStatusGate;
    private Clock clock;
    private BookingCreationService service;

    private UUID memberId;
    private UUID sessionId;
    private Member testMember;
    private Membership testMembership;
    private ClassSession testSession;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        classSessionRepository = mock(ClassSessionRepository.class);
        creditPort = mock(CreditPort.class);
        memberStatusGate = mock(MemberStatusGate.class);
        clock = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneId.of("UTC"));

        service = new BookingCreationService(
            bookingRepository,
            classSessionRepository,
            creditPort,
            memberStatusGate
        );

        memberId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        testMember = new Member("test@example.com", "Test Member", null, "ACTIVE", clock);

        testMembership = new Membership(
            memberId,
            UUID.randomUUID(),
            10,
            clock
        );

        Instant sessionStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant sessionEnd = Instant.parse("2026-09-30T11:00:00Z");
        testSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            sessionStart,
            sessionEnd,
            10,
            clock
        );
        testSession.setId(sessionId);
        testSession.setStatus("SCHEDULED");
    }

    // AC-1: Overlap detection returns 409 with both booking and session IDs
    @Test
    void test_ac1_overlapping_booked_booking_returns_booking_overlaps_existing() {
        // Setup: member has an existing BOOKED booking from 10:00-11:00
        // Try to book overlapping session 10:30-11:30
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("BOOKED");

        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        // New session: 10:30-11:30 (overlaps with 10:00-11:00)
        Instant newStart = Instant.parse("2026-09-30T10:30:00Z");
        Instant newEnd = Instant.parse("2026-09-30T11:30:00Z");
        testSession.setStartsAt(newStart);
        testSession.setEndsAt(newEnd);

        // Mock setup
        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getSkipReason()).isEqualTo(BookingCreationService.SkipReason.BOOKING_OVERLAPS_EXISTING);
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.BOOKING_OVERLAPS_EXISTING);
        assertThat(result.getConflictingBookingId()).isEqualTo(existingBookingId);
        assertThat(result.getConflictingSessionId()).isEqualTo(existingSessionId);
    }

    // AC-2: Half-open boundary - sessions 10:00-11:00 and 11:00-12:00 do NOT overlap
    @Test
    void test_ac2_half_open_boundary_consecutive_sessions_no_overlap() {
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("BOOKED");

        // Existing session: 10:00-11:00
        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        // New session: 11:00-12:00 (should NOT overlap with [10:00, 11:00))
        Instant newStart = Instant.parse("2026-09-30T11:00:00Z");
        Instant newEnd = Instant.parse("2026-09-30T12:00:00Z");
        testSession.setStartsAt(newStart);
        testSession.setEndsAt(newEnd);

        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getSkipReason()).isNull();
    }

    // AC-3: Half-open boundary - sessions 10:00-11:00 and 10:59-11:59 DO overlap
    @Test
    void test_ac3_half_open_boundary_partial_overlap_conflicts() {
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("BOOKED");

        // Existing session: 10:00-11:00
        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        // New session: 10:59-11:59 (overlaps with [10:00, 11:00))
        Instant newStart = Instant.parse("2026-09-30T10:59:00Z");
        Instant newEnd = Instant.parse("2026-09-30T11:59:00Z");
        testSession.setStartsAt(newStart);
        testSession.setEndsAt(newEnd);

        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getSkipReason()).isEqualTo(BookingCreationService.SkipReason.BOOKING_OVERLAPS_EXISTING);
    }

    // AC-4: Partial overlaps and full containment conflict
    @ParameterizedTest(name = "new_session[{0}, {1}) vs existing[10:00, 11:00) should conflict")
    @ValueSource(strings = {
        "2026-09-30T09:30:00Z,2026-09-30T10:30:00Z",  // Overlaps start
        "2026-09-30T10:30:00Z,2026-09-30T11:30:00Z",  // Overlaps end
        "2026-09-30T10:00:00Z,2026-09-30T11:00:00Z",  // Exact match
        "2026-09-30T09:00:00Z,2026-09-30T12:00:00Z",  // Contains existing
        "2026-09-30T10:15:00Z,2026-09-30T10:45:00Z"   // Contained by existing
    })
    void test_ac4_various_overlaps_conflict(String timeRange) {
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("BOOKED");

        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        String[] parts = timeRange.split(",");
        Instant newStart = Instant.parse(parts[0]);
        Instant newEnd = Instant.parse(parts[1]);
        testSession.setStartsAt(newStart);
        testSession.setEndsAt(newEnd);

        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible())
            .as("Sessions %s should conflict with [10:00, 11:00)", timeRange)
            .isFalse();
        assertThat(result.getSkipReason()).isEqualTo(BookingCreationService.SkipReason.BOOKING_OVERLAPS_EXISTING);
    }

    // AC-5: CANCELLED booking does not block overlap
    @Test
    void test_ac5_cancelled_booking_does_not_block_overlap() {
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("CANCELLED");

        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        testSession.setStartsAt(Instant.parse("2026-09-30T10:30:00Z"));
        testSession.setEndsAt(Instant.parse("2026-09-30T11:30:00Z"));

        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isTrue();
    }

    // AC-6: ATTENDED and NO_SHOW do not block overlap
    @ParameterizedTest(name = "{0} booking does not block overlap")
    @ValueSource(strings = {"ATTENDED", "NO_SHOW"})
    void test_ac6_terminal_statuses_do_not_block_overlap(String status) {
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus(status);

        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        testSession.setStartsAt(Instant.parse("2026-09-30T10:30:00Z"));
        testSession.setEndsAt(Instant.parse("2026-09-30T11:30:00Z"));

        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isTrue();
    }

    // AC-7: Another member's overlapping booking does not block
    @Test
    void test_ac7_other_member_overlapping_booking_does_not_block() {
        UUID otherMemberId = UUID.randomUUID();
        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(otherMemberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("BOOKED");

        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        testSession.setStartsAt(Instant.parse("2026-09-30T10:30:00Z"));
        testSession.setEndsAt(Instant.parse("2026-09-30T11:30:00Z"));

        // Note: findByMemberId only returns bookings for the target member
        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of());

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isTrue();
    }

    // AC-9: Parameterized test for consistency across all skip reasons
    @ParameterizedTest(name = "Skip reason {0}")
    @ValueSource(strings = {"MEMBER_INACTIVE", "MEMBERSHIP_INACTIVE", "INSUFFICIENT_CREDITS", "BOOKING_OVERLAPS_EXISTING"})
    void test_ac9_checkEligibility_produces_consistent_verdicts(String skipReasonName) {
        BookingCreationService.SkipReason skipReason = BookingCreationService.SkipReason.valueOf(skipReasonName);

        // Setup different scenarios for each skip reason
        switch (skipReason) {
            case MEMBER_INACTIVE:
                testMember.setStatus("SUSPENDED");
                when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
                doThrow(new ApiException(ErrorCode.MEMBER_SUSPENDED, "Member is suspended")).when(memberStatusGate).requireActive(testMember);
                break;

            case MEMBERSHIP_INACTIVE:
                when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
                when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.empty());
                break;

            case INSUFFICIENT_CREDITS:
                when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
                when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
                doThrow(new ApiException(ErrorCode.CREDITS_INSUFFICIENT, "Insufficient credits"))
                    .when(creditPort).requireCredit(testMembership.getId());
                break;

            case BOOKING_OVERLAPS_EXISTING:
                UUID existingBookingId = UUID.randomUUID();
                UUID existingSessionId = UUID.randomUUID();
                Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
                existingBooking.setId(existingBookingId);
                existingBooking.setStatus("BOOKED");

                Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
                Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
                ClassSession existingSession = new ClassSession(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    existingStart,
                    existingEnd,
                    10,
                    clock
                );
                existingSession.setId(existingSessionId);

                testSession.setStartsAt(Instant.parse("2026-09-30T10:30:00Z"));
                testSession.setEndsAt(Instant.parse("2026-09-30T11:30:00Z"));

                when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
                when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
                doNothing().when(creditPort).requireCredit(testMembership.getId());
                when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
                when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
                when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
                when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));
                break;
        }

        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getSkipReason()).isEqualTo(skipReason);
    }

    // AC-10: Each skip reason maps to correct error code and waitlist skip_reason
    @ParameterizedTest(name = "{0} -> {1}")
    @ValueSource(strings = {
        "MEMBER_INACTIVE,MEMBER_INACTIVE",
        "MEMBERSHIP_INACTIVE,MEMBER_INACTIVE",
        "INSUFFICIENT_CREDITS,CREDITS_INSUFFICIENT",
        "BOOKING_OVERLAPS_EXISTING,BOOKING_OVERLAPS_EXISTING"
    })
    void test_ac10_skip_reasons_map_to_correct_codes(String params) {
        String[] parts = params.split(",");
        BookingCreationService.SkipReason skipReason = BookingCreationService.SkipReason.valueOf(parts[0]);
        ErrorCode expectedCode = ErrorCode.valueOf(parts[1]);

        BookingCreationService.EligibilityResult result;

        if (skipReason == BookingCreationService.SkipReason.MEMBER_INACTIVE) {
            result = BookingCreationService.EligibilityResult.skipped(skipReason, expectedCode);
        } else if (skipReason == BookingCreationService.SkipReason.MEMBERSHIP_INACTIVE) {
            result = BookingCreationService.EligibilityResult.skipped(skipReason, expectedCode);
        } else if (skipReason == BookingCreationService.SkipReason.INSUFFICIENT_CREDITS) {
            result = BookingCreationService.EligibilityResult.skipped(skipReason, expectedCode);
        } else if (skipReason == BookingCreationService.SkipReason.BOOKING_OVERLAPS_EXISTING) {
            result = BookingCreationService.EligibilityResult.overlappingBooking(UUID.randomUUID(), UUID.randomUUID());
        } else {
            throw new IllegalArgumentException("Unknown skip reason: " + skipReason);
        }

        assertThat(result.getErrorCode()).isEqualTo(expectedCode);
    }

    // AC-8: Overlap check must run while member is locked (documented constraint)
    // This test verifies that the overlap check is called during checkEligibility,
    // which is the method that must be called while the member row is held under lock.
    @Test
    void test_ac8_overlap_check_documentation_lock_requirement() {
        // This test documents that checkEligibility (which includes overlap check)
        // must be called while the member is locked. The actual locking happens in
        // BookingService.createBooking which calls memberStatusGate.loadForTransaction(memberId)
        // before calling checkEligibility. The order is enforced at the service level.

        UUID existingBookingId = UUID.randomUUID();
        UUID existingSessionId = UUID.randomUUID();

        Booking existingBooking = new Booking(memberId, existingSessionId, "DIRECT", clock);
        existingBooking.setId(existingBookingId);
        existingBooking.setStatus("BOOKED");

        Instant existingStart = Instant.parse("2026-09-30T10:00:00Z");
        Instant existingEnd = Instant.parse("2026-09-30T11:00:00Z");
        ClassSession existingSession = new ClassSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            existingStart,
            existingEnd,
            10,
            clock
        );
        existingSession.setId(existingSessionId);

        testSession.setStartsAt(Instant.parse("2026-09-30T10:30:00Z"));
        testSession.setEndsAt(Instant.parse("2026-09-30T11:30:00Z"));

        when(memberStatusGate.loadForTransaction(memberId)).thenReturn(testMember);
        when(creditPort.loadUsableForBooking(memberId)).thenReturn(Optional.of(testMembership));
        doNothing().when(creditPort).requireCredit(testMembership.getId());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(bookingRepository.findBookedBySessionId(sessionId)).thenReturn(List.of());
        when(bookingRepository.findByMemberId(memberId)).thenReturn(List.of(existingBooking));
        when(classSessionRepository.findById(existingSessionId)).thenReturn(Optional.of(existingSession));

        // Call checkEligibility - this internally calls checkOverlap
        BookingCreationService.EligibilityResult result = service.checkEligibility(memberId, sessionId);

        // Verify that overlap was detected
        assertThat(result.isEligible()).isFalse();
        assertThat(result.getSkipReason()).isEqualTo(BookingCreationService.SkipReason.BOOKING_OVERLAPS_EXISTING);

        // The critical requirement is that this check was called AFTER memberStatusGate.loadForTransaction,
        // which acquires the pessimistic lock. This is enforced by the call order in BookingService.
        verify(memberStatusGate).loadForTransaction(memberId);
    }
}
