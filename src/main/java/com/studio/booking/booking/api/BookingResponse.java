package com.studio.booking.booking.api;

import com.studio.booking.booking.domain.Booking;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
    "id", "memberId", "sessionId", "membershipId", "status", "source",
    "creditDeducted", "createdAt", "updatedAt"
})
public record BookingResponse(
    String id,
    String memberId,
    String sessionId,
    String membershipId,
    String status,
    String source,
    boolean creditDeducted,
    String createdAt,
    String updatedAt
) {
    public static BookingResponse from(Booking booking, boolean creditDeducted) {
        return new BookingResponse(
            booking.getId().toString(),
            booking.getMemberId().toString(),
            booking.getSessionId().toString(),
            booking.getMembershipId().toString(),
            booking.getStatus(),
            booking.getSource(),
            creditDeducted,
            booking.getCreatedAt().toString(),
            booking.getUpdatedAt().toString()
        );
    }
}
