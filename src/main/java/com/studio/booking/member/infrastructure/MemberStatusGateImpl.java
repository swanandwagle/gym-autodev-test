package com.studio.booking.member.infrastructure;

import com.studio.booking.member.application.MemberStatusGate;
import com.studio.booking.member.domain.Member;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter that implements MemberStatusGate using the MemberRepository.
 *
 * Package-private; only the interface is exported from the member module's application package.
 * Delegates locking operations to the repository; transactional boundary is provided by
 * MemberStatusGateService in the application package.
 */
@Component
class MemberStatusGateImpl implements MemberStatusGate {

    private final MemberRepository memberRepository;

    MemberStatusGateImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public Member loadForTransaction(UUID memberId) {
        return memberRepository.findByIdWithWriteLock(memberId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBER_NOT_FOUND,
                "Member not found with id: " + memberId
            ));
    }

    @Override
    public void requireActive(Member member) {
        if ("SUSPENDED".equals(member.getStatus())) {
            throw new ApiException(
                ErrorCode.MEMBER_SUSPENDED,
                "Member is suspended and cannot perform this action"
            );
        }
    }

    @Override
    public boolean isActive(Member member) {
        return "ACTIVE".equals(member.getStatus());
    }
}
