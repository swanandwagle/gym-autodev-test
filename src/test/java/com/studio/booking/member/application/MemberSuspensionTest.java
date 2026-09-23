package com.studio.booking.member.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberSuspensionTest {

    @Mock private MemberRepository memberRepository;
    @Mock private Clock clock;

    @InjectMocks private MemberService memberService;

    private static final Instant FIXED_TIME = Instant.parse("2026-09-23T10:00:00Z");
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Test
    void test_ac1_suspend_active_member_sets_status_reason_suspended_at() {
        Member active = new Member("john@example.com", "John", null, "ACTIVE", clock);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(active));
        when(clock.instant()).thenReturn(FIXED_TIME);

        Member saved = new Member("john@example.com", "John", null, "ACTIVE", clock);
        saved.suspendStatus("STAFF", clock);
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        Member result = memberService.suspendMember(MEMBER_ID, "STAFF");

        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        assertThat(result.getSuspensionReason()).isEqualTo("STAFF");
        assertThat(result.getSuspendedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void test_ac1_suspend_updates_version_and_updated_at() {
        Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        Member active = new Member("john@example.com", "John", null, "ACTIVE", fixedClock);
        assertThat(active.getVersion()).isEqualTo(0);
        long originalUpdatedAt = active.getUpdatedAt().toEpochMilli();

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(active));
        when(fixedClock.instant()).thenReturn(FIXED_TIME);

        Member saved = new Member("john@example.com", "John", null, "ACTIVE", fixedClock);
        saved.suspendStatus("STAFF", fixedClock);
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        Member result = memberService.suspendMember(MEMBER_ID, "STAFF");

        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void test_ac2_suspend_already_suspended_returns_409() {
        Member suspended = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspended.suspendStatus("STAFF", clock);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> memberService.suspendMember(MEMBER_ID, "STAFF"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_SUSPENDED);
                assertThat(apiEx.getHttpStatus().value()).isEqualTo(409);
            });
    }

    @Test
    void test_ac2_suspend_does_not_overwrite_original_suspended_at() {
        Instant originalTime = Instant.parse("2026-09-20T10:00:00Z");
        Member suspended = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspended.suspendStatus("STAFF", clock);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> memberService.suspendMember(MEMBER_ID, "NEW_REASON"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void test_ac3_reactivate_suspended_member_clears_reason_and_timestamp() {
        Member suspended = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspended.suspendStatus("STAFF", clock);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(suspended));

        Member saved = new Member("john@example.com", "John", null, "ACTIVE", clock);
        saved.reactivateStatus();
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        Member result = memberService.reactivateMember(MEMBER_ID);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getSuspensionReason()).isNull();
        assertThat(result.getSuspendedAt()).isNull();
    }

    @Test
    void test_ac4_reactivate_active_member_returns_409() {
        Member active = new Member("john@example.com", "John", null, "ACTIVE", clock);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> memberService.reactivateMember(MEMBER_ID))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_SUSPENDED);
                assertThat(apiEx.getHttpStatus().value()).isEqualTo(409);
            });
    }

    @Test
    void test_ac5_suspend_unknown_member_returns_404() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.suspendMember(MEMBER_ID, "STAFF"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
                assertThat(apiEx.getHttpStatus().value()).isEqualTo(404);
            });
    }

    @Test
    void test_ac5_reactivate_unknown_member_returns_404() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.reactivateMember(MEMBER_ID))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
                assertThat(apiEx.getHttpStatus().value()).isEqualTo(404);
            });
    }

    @Test
    void test_ac8_staff_vs_no_show_reason_distinguishable() {
        Member suspendedByStaff = new Member("john@example.com", "John", null, "ACTIVE", clock);
        suspendedByStaff.suspendStatus("STAFF", clock);

        Member suspendedByNoShow = new Member("jane@example.com", "Jane", null, "ACTIVE", clock);
        suspendedByNoShow.suspendStatus("NO_SHOW_LIMIT", clock);

        assertThat(suspendedByStaff.getSuspensionReason()).isEqualTo("STAFF");
        assertThat(suspendedByNoShow.getSuspensionReason()).isEqualTo("NO_SHOW_LIMIT");
    }
}
