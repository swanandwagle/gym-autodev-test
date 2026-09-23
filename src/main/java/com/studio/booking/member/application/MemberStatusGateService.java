package com.studio.booking.member.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberStatusGateImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application-layer service that provides the transactional boundary for
 * member status gate operations. This service wraps the infrastructure adapter
 * and applies @Transactional to ensure lock acquisition and release are
 * properly managed by Spring's transaction framework.
 */
@Service
public class MemberStatusGateService implements MemberStatusGate {

    private final MemberStatusGate adapter;

    public MemberStatusGateService(MemberStatusGateImpl adapter) {
        this.adapter = adapter;
    }

    @Override
    @Transactional
    public Member loadForTransaction(UUID memberId) {
        return adapter.loadForTransaction(memberId);
    }

    @Override
    public void requireActive(Member member) {
        adapter.requireActive(member);
    }

    @Override
    public boolean isActive(Member member) {
        return adapter.isActive(member);
    }
}
