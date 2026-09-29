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
import com.studio.booking.shared.notification.NotificationLog;
import com.studio.booking.shared.notification.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CreditPort creditPort;
    private final MemberStatusGate memberStatusGate;
    private final NotificationLogRepository notificationLogRepository;
    private final Clock clock;

    public BookingService(
        BookingRepository bookingRepository,
        ClassSessionRepository classSessionRepository,
        CreditPort creditPort,
        MemberStatusGate memberStatusGate,
        NotificationLogRepository notificationLogRepository,
        Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.classSessionRepository = classSessionRepository;
        this.creditPort = creditPort;
        this.memberStatusGate = memberStatusGate;
        this.notificationLogRepository = notificationLogRepository;
        this.clock = clock;
    }

    @Transactional
    public BookingCreateResult createBooking(UUID memberId, UUID sessionId, String actorType) {
        // Load and lock member first (suspension check first per AC-9)
        Member member = memberStatusGate.loadForTransaction(memberId);
        memberStatusGate.requireActive(member);

        // Check membership is active (membership must be usable for booking)
        Optional<Membership> membershipOpt = creditPort.loadUsableForBooking(memberId);
        if (membershipOpt.isEmpty()) {
            throw new ApiException(
                ErrorCode.MEMBER_INACTIVE,
                "The member account is inactive and cannot perform this action",
                null
            );
        }
        Membership membership = membershipOpt.get();

        // Check credits are available BEFORE checking capacity (AC-8: credits before capacity)
        creditPort.requireCredit(membership.getId());

        // Load and lock session for capacity check
        ClassSession session = classSessionRepository.findByIdWithLock(sessionId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.SESSION_NOT_FOUND,
                "No class session found with the given identifier",
                null
            ));

        // Validate session status (must not be cancelled)
        if ("CANCELLED".equals(session.getStatus())) {
            throw new ApiException(
                ErrorCode.SESSION_CANCELLED,
                "The session has been cancelled and cannot be booked",
                null
            );
        }

        // Validate session has started (must be in future)
        Instant now = Instant.now(clock);
        if (now.isAfter(session.getStartsAt())) {
            throw new ApiException(
                ErrorCode.SESSION_NOT_BOOKABLE,
                "The session is not open for booking",
                null
            );
        }

        // Check for duplicate booking (must be non-cancelled only per AC-13)
        boolean existingNonCancelledBooking = bookingRepository.findBookedBySessionId(sessionId).stream()
            .anyMatch(b -> b.getMemberId().equals(memberId) && !"CANCELLED".equals(b.getStatus()));
        if (existingNonCancelledBooking) {
            throw new ApiException(
                ErrorCode.DUPLICATE_BOOKING,
                "A non-cancelled booking already exists for this member and session",
                null
            );
        }

        // Check session has capacity
        if (session.getBookedCount() >= session.getCapacity()) {
            throw new ApiException(
                ErrorCode.SESSION_FULL,
                "The session has no remaining capacity",
                null
            );
        }

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
        session.setUpdatedAt(Instant.now(clock));
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
