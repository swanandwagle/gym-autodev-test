package com.studio.booking.booking.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.studio.booking.booking.application.BookingDetail;
import com.studio.booking.booking.domain.Booking;
import java.time.Instant;

@JsonPropertyOrder({"id", "memberId", "sessionId", "membershipId", "status", "source", "bookedAt", "cancellationType", "creditRefunded", "checkedInAt", "checkedInBy", "session", "creditDeducted", "createdAt", "updatedAt"})
public record BookingResponse(
    String id, String memberId, String sessionId, String membershipId, String status, String source,
    String bookedAt, String cancellationType, Boolean creditRefunded, Instant checkedInAt,
    String checkedInBy, SessionDetail session, boolean creditDeducted, String createdAt, String updatedAt
) {
    public record SessionDetail(String id, Instant startsAt, Instant endsAt, String classTypeName, String instructorName, String roomName) {}

    public static BookingResponse from(Booking booking, boolean creditDeducted) {
        return new BookingResponse(booking.getId().toString(), booking.getMemberId().toString(),
            booking.getSessionId().toString(), booking.getMembershipId().toString(), apiStatus(booking.getStatus()),
            apiSource(booking.getSource()), booking.getCreatedAt().toString(), apiCancellation(booking.getCancellationType()),
            booking.getCreditRefunded(), booking.getCheckedInAt(), booking.getCheckedInBy(), null, creditDeducted,
            booking.getCreatedAt().toString(), booking.getUpdatedAt().toString());
    }

    public static BookingResponse from(BookingDetail b) {
        return new BookingResponse(b.getId().toString(), b.getMemberId().toString(), b.getSessionId().toString(),
            b.getMembershipId().toString(), apiStatus(b.getStatus()), apiSource(b.getSource()), b.getBookedAt().toString(),
            apiCancellation(b.getCancellationType()), b.getCreditRefunded(), b.getCheckedInAt(), b.getCheckedInBy(),
            new SessionDetail(b.getSessionId().toString(), b.getStartsAt(), b.getEndsAt(), b.getClassTypeName(), b.getInstructorName(), b.getRoomName()),
            false, b.getBookedAt().toString(), b.getBookedAt().toString());
    }

    private static String apiStatus(String value) { return "CHECKED_IN".equals(value) ? "ATTENDED" : value; }
    private static String apiSource(String value) { return value; }
    private static String apiCancellation(String value) { return "NORMAL".equals(value) ? "STANDARD" : value; }
}
