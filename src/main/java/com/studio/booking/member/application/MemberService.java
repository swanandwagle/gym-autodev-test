package com.studio.booking.member.application;

import com.studio.booking.member.api.RegisterMemberRequest;
import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final Clock clock;

    public MemberService(MemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public Member registerMember(RegisterMemberRequest request) {
        String normalizedEmail = request.email();

        memberRepository.findByEmailIgnoreCase(normalizedEmail)
            .ifPresent(existing -> {
                throw new ApiException(
                    ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS,
                    "A member with this email address already exists"
                );
            });

        String trimmedName = request.fullName().trim();
        Member member = new Member(
            normalizedEmail,
            trimmedName,
            request.phone(),
            "ACTIVE",
            clock
        );

        return memberRepository.save(member);
    }
}
