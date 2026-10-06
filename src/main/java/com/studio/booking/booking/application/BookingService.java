package com.studio.booking.booking.application;

import com.studio.booking.booking.domain.Booking;
import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.member.application.MemberStatusGate;
import com.studio.booking.membership.application.CreditPort;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.notification.NotificationLog;
import com.studio.booking.shared.notification.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.time.Duration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CreditPort creditPort;
    private final MemberStatusGate memberStatusGate;
    private final NotificationLogRepository notificationLogRepository;
    private final Clock clock;
    private final BookingCreationService bookingCreationService;

    public BookingService(
        BookingRepository bookingRepository,
        ClassSessionRepository classSessionRepository,
        CreditPort creditPort,
        MemberStatusGate memberStatusGate,
        NotificationLogRepository notificationLogRepository,
        Clock clock,
        BookingCreationService bookingCreationService
    ) {
        this.bookingRepository = bookingRepository;
        this.classSessionRepository = classSessionRepository;
        this.creditPort = creditPort;
        this.memberStatusGate = memberStatusGate;
        this.notificationLogRepository = notificationLogRepository;
        this.clock = clock;
        this.bookingCreationService = bookingCreationService;
    }

    @Transactional
    public BookingCreateResult createBooking(UUID memberId, UUID sessionId, String actorType) {
        // Load and lock session first (lock acquisition order: session → member → membership)
        ClassSession session = classSessionRepository.findByIdWithLock(sessionId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.SESSION_NOT_FOUND,
                "No class session found with the given identifier"
            ));

        // Check eligibility using shared service (acquires member lock inside, includes overlap check)
        BookingCreationService.EligibilityResult eligibility = bookingCreationService.checkEligibility(memberId, sessionId);
        if (!eligibility.isEligible()) {
            if (eligibility.getSkipReason() == BookingCreationService.SkipReason.BOOKING_OVERLAPS_EXISTING) {
                throw new ApiException(
                    ErrorCode.BOOKING_OVERLAPS_EXISTING,
                    "Member has a booking that overlaps with this session"
                );
            }
            // For other skip reasons, throw appropriate error
            throw new ApiException(
                eligibility.getErrorCode(),
                "Booking eligibility check failed"
            );
        }

        // Validate session status (must not be cancelled)
        if ("CANCELLED".equals(session.getStatus())) {
            throw new ApiException(
                ErrorCode.SESSION_CANCELLED,
                "The session has been cancelled and cannot be booked"
            );
        }

        // Validate session has started (must be in future)
        Instant now = Instant.now(clock);
        if (now.isAfter(session.getStartsAt())) {
            throw new ApiException(
                ErrorCode.SESSION_NOT_BOOKABLE,
                "The session is not open for booking"
            );
        }

        // Check session has capacity
        if (session.getBookedCount() >= session.getCapacity()) {
            throw new ApiException(
                ErrorCode.SESSION_FULL,
                "The session has no remaining capacity"
            );
        }

        // Load membership for credit deduction
        Optional<Membership> membershipOpt = creditPort.loadUsableForBooking(memberId);
        if (membershipOpt.isEmpty()) {
            throw new ApiException(
                ErrorCode.MEMBER_INACTIVE,
                "The member account is inactive and cannot perform this action"
            );
        }
        Membership membership = membershipOpt.get();

        // Determine source
        String source = "STAFF".equalsIgnoreCase(actorType) ? "STAFF" : "DIRECT";

        // Deduct credit first to verify membership can be charged before modifying state
        boolean creditDeducted = !membership.isUnlimited();
        if (creditDeducted) {
            creditPort.deduct(membership.getId(), "BOOKING");
        }

        // Create booking (must be atomic with credit and session update)
        Booking booking = new Booking(memberId, sessionId, source, clock);
        booking.setMembershipId(membership.getId());
        booking = bookingRepository.save(booking);

        // Increment booked count (must be atomic with booking and credit)
        session.setBookedCount(session.getBookedCount() + 1);
        classSessionRepository.save(session);

        // Write notification log (must be atomic)
        NotificationLog notification = new NotificationLog(
            memberId,
            "BOOKING_CONFIRMED",
            "LOG",
            "{\"bookingId\":\"" + booking.getId() + "\",\"sessionId\":\"" + sessionId + "\"}",
            "SYSTEM"
        );
        notificationLogRepository.save(notification);

        return new BookingCreateResult(booking, creditDeducted);
    }

    @Transactional(readOnly = true)
    public BookingRepository.BookingDetail getBooking(UUID bookingId) {
        return bookingRepository.findDetailById(bookingId).orElseThrow(() ->
            new ApiException(ErrorCode.BOOKING_NOT_FOUND, "No booking found with the given identifier"));
    }

    @Transactional
    public BookingCancellationResult cancelBooking(UUID bookingId) {
        Booking initial = bookingRepository.findById(bookingId).orElseThrow(() ->
            new ApiException(ErrorCode.BOOKING_NOT_FOUND, "No booking found with the given identifier"));
        ClassSession session = classSessionRepository.findByIdWithLock(initial.getSessionId()).orElseThrow(() ->
            new ApiException(ErrorCode.SESSION_NOT_FOUND, "No class session found with the given identifier"));
        Booking booking = bookingRepository.findByIdWithLock(bookingId).orElseThrow(() ->
            new ApiException(ErrorCode.BOOKING_NOT_FOUND, "No booking found with the given identifier"));
        Instant now = Instant.now(clock);
        if (!"BOOKED".equals(booking.getStatus())) {
            throw new ApiException(ErrorCode.BOOKING_NOT_CANCELLABLE,
                "Booking status " + booking.getStatus() + " cannot be cancelled");
        }
        if (!now.isBefore(session.getStartsAt())) {
            throw new ApiException(ErrorCode.SESSION_ALREADY_STARTED, "The session has already started");
        }
        boolean standard = Duration.between(now, session.getStartsAt()).compareTo(Duration.ofHours(4)) >= 0;
        boolean unlimited = creditPort.isUnlimited(booking.getMembershipId());
        boolean refunded = standard && !unlimited;
        if (refunded) creditPort.refund(booking.getMembershipId(), "CANCEL_REFUND");
        booking.setStatus("CANCELLED");
        booking.setCancellationType(standard ? "STANDARD" : "LATE");
        booking.setCreditRefunded(refunded);
        booking.setCancelledAt(now);
        bookingRepository.save(booking);
        session.setBookedCount(Math.max(0, session.getBookedCount() - 1));
        classSessionRepository.save(session);
        notificationLogRepository.save(new NotificationLog(booking.getMemberId(), "BOOKING_CANCELLED", "LOG",
            "{\"bookingId\":\"" + bookingId + "\",\"sessionId\":\"" + session.getId() + "\"}", "SYSTEM", clock));
        return new BookingCancellationResult(bookingId, now, standard ? "STANDARD" : "LATE", refunded, null);
    }

    public record BookingCancellationResult(UUID bookingId, Instant cancelledAt, String cancellationType,
                                            boolean creditRefunded, UUID promotedWaitlistEntryId) {}

    @Transactional(readOnly = true)
    public Page<BookingRepository.BookingDetail> getMemberHistory(UUID memberId, List<String> statuses,
            Instant from, Instant to, boolean upcomingOnly, int page, int size) {
        try {
            memberStatusGate.loadForTransaction(memberId);
        } catch (ApiException e) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND, "No member found with the given identifier");
        }
        List<String> dbStatuses = statuses == null ? null : statuses.stream()
            .map(s -> "ATTENDED".equals(s) ? "CHECKED_IN" : s).toList();
        String statusFilter = dbStatuses == null ? null : String.join(",", dbStatuses);
        return bookingRepository.findHistory(memberId, statusFilter, from, to, upcomingOnly, Instant.now(clock),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startsAt")));
    }

    public static class BookingCreateResult {
        private final Booking booking;
        private final boolean creditDeducted;

        public BookingCreateResult(Booking booking, boolean creditDeducted) {
            this.booking = booking;
            this.creditDeducted = creditDeducted;
        }

        public Booking getBooking() {
            return booking;
        }

        public boolean isCreditDeducted() {
            return creditDeducted;
        }
    }
}
