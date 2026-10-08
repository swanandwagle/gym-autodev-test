package com.studio.booking.booking.application;

import java.time.Instant;
import java.util.UUID;

public interface BookingDetail {
    UUID getId();
    UUID getMemberId();
    UUID getSessionId();
    UUID getMembershipId();
    String getStatus();
    String getSource();
    Instant getBookedAt();
    String getCancellationType();
    Boolean getCreditRefunded();
    Instant getCheckedInAt();
    String getCheckedInBy();
    Instant getStartsAt();
    Instant getEndsAt();
    String getClassTypeName();
    String getInstructorName();
    String getRoomName();
}
