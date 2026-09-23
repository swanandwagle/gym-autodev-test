package com.studio.booking.member.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.member.infrastructure.MemberStatusGateImpl;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberStatusGateTest {

    @Mock private MemberRepository memberRepository;
    @Mock private Clock clock;

    private MemberStatusGate gate() {
        MemberStatusGateImpl adapter = new MemberStatusGateImpl(memberRepository);
        return new MemberStatusGateService(adapter);
    }

    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Test
    void test_ac3_requireActive_throws_403_for_suspended() {
        Member suspended = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspended.suspendStatus("STAFF", clock);

        assertThatThrownBy(() -> gate().requireActive(suspended))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_SUSPENDED);
                assertThat(apiEx.getHttpStatus().value()).isEqualTo(403);
            });
    }

    @Test
    void test_ac3_requireActive_returns_normally_for_active() {
        Member active = new Member("john@example.com", "John", null, "ACTIVE", clock);

        gate().requireActive(active);
    }

    @Test
    void test_ac4a_isActive_returns_true_for_active() {
        Member active = new Member("john@example.com", "John", null, "ACTIVE", clock);

        boolean result = gate().isActive(active);

        assertThat(result).isTrue();
    }

    @Test
    void test_ac4b_isActive_returns_false_for_suspended() {
        Member suspended = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspended.suspendStatus("STAFF", clock);

        boolean result = gate().isActive(suspended);

        assertThat(result).isFalse();
    }

    @Test
    void test_ac4c_isActive_never_throws_for_active() {
        Member active = new Member("john@example.com", "John", null, "ACTIVE", clock);

        assertThat(gate().isActive(active)).isTrue();
    }

    @Test
    void test_ac4c_isActive_never_throws_for_suspended() {
        Member suspended = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspended.suspendStatus("STAFF", clock);

        assertThat(gate().isActive(suspended)).isFalse();
    }

    @Test
    void test_ac1_loadForTransaction_throws_not_found_for_missing_member() {
        when(memberRepository.findByIdWithWriteLock(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate().loadForTransaction(MEMBER_ID))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
            });
    }

    @Test
    void test_ac1_loadForTransaction_returns_member_when_found() {
        Member member = new Member("john@example.com", "John", null, "ACTIVE", clock);
        when(memberRepository.findByIdWithWriteLock(MEMBER_ID)).thenReturn(Optional.of(member));

        Member result = gate().loadForTransaction(MEMBER_ID);

        assertThat(result).isEqualTo(member);
    }
}
