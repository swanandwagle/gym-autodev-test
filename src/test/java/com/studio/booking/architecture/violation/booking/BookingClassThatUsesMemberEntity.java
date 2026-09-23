package com.studio.booking.architecture.violation.booking;

import com.studio.booking.member.domain.Member;

/** Deliberate violation: booking package directly using member domain entity. */
public class BookingClassThatUsesMemberEntity {
    Member memberRef;
}
