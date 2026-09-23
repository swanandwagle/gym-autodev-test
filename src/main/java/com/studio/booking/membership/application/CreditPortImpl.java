package com.studio.booking.membership.application;

import com.studio.booking.membership.domain.CreditTransaction;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.infrastructure.CreditTransactionRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditPortImpl implements CreditPort {

    private final MembershipRepository membershipRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final Clock clock;

    public CreditPortImpl(
        MembershipRepository membershipRepository,
        CreditTransactionRepository creditTransactionRepository,
        Clock clock
    ) {
        this.membershipRepository = membershipRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.clock = clock;
    }

    @Override
    public Optional<Membership> loadUsableForBooking(UUID memberId) {
        Instant now = Instant.now(clock);
        return membershipRepository.findActiveByMemberIdWithWriteLock(memberId)
            .filter(m -> {
                Instant startsAt = m.getStartsAt();
                Instant expiresAt = m.getExpiresAt();
                return !now.isBefore(startsAt) && now.isBefore(expiresAt);
            });
    }

    @Override
    public void requireCredit(UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_NOT_FOUND,
                "No membership exists for the given ID",
                null
            ));

        if (!membership.isUnlimited() && membership.getCreditsRemaining() <= 0) {
            throw new ApiException(
                ErrorCode.MEMBERSHIP_NO_CREDITS,
                "The membership has no remaining credits",
                null
            );
        }
    }

    @Override
    public boolean hasCredit(UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
            .orElse(null);

        if (membership == null) {
            return false;
        }

        if (membership.isUnlimited()) {
            return true;
        }

        return membership.getCreditsRemaining() > 0;
    }

    @Override
    @Transactional
    public void deduct(UUID membershipId, String reason) {
        Membership membership = membershipRepository.findByIdWithLock(membershipId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_NOT_FOUND,
                "No membership exists for the given ID",
                null
            ));

        if (membership.isUnlimited()) {
            return;
        }

        int currentBalance = membership.getCreditsRemaining();
        if (currentBalance <= 0) {
            throw new ApiException(
                ErrorCode.MEMBERSHIP_NO_CREDITS,
                "The membership has no remaining credits",
                null
            );
        }

        int newBalance = currentBalance - 1;
        membership.setCreditsRemaining(newBalance);
        membership.setUpdatedAt(Instant.now(clock));
        membershipRepository.save(membership);

        CreditTransaction transaction = new CreditTransaction(
            membershipId,
            -1,
            reason,
            newBalance,
            Instant.now(clock)
        );
        creditTransactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void refund(UUID membershipId, String reason) {
        Membership membership = membershipRepository.findByIdWithLock(membershipId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_NOT_FOUND,
                "No membership exists for the given ID",
                null
            ));

        if (membership.isUnlimited()) {
            return;
        }

        int currentBalance = membership.getCreditsRemaining();
        int newBalance = currentBalance + 1;
        membership.setCreditsRemaining(newBalance);
        membership.setUpdatedAt(Instant.now(clock));
        membershipRepository.save(membership);

        CreditTransaction transaction = new CreditTransaction(
            membershipId,
            1,
            reason,
            newBalance,
            Instant.now(clock)
        );
        creditTransactionRepository.save(transaction);
    }
}
