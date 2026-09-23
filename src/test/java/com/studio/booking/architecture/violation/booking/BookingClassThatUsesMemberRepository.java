package com.studio.booking.architecture.violation.booking;

import com.studio.booking.member.infrastructure.MemberRepository;

/** Deliberate violation: booking package directly using member repository. */
public class BookingClassThatUsesMemberRepository {
    MemberRepository repositoryRef;
}
