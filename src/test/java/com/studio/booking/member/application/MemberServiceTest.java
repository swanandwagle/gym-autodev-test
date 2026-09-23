package com.studio.booking.member.application;

import com.studio.booking.member.api.RegisterMemberRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private Clock clock;

    @InjectMocks private MemberService memberService;

    private static final Instant FIXED_TIME = Instant.parse("2026-09-23T10:00:00Z");

    @Test
    void test_ac2_member_created_with_active_status_null_suspension_reason_version_zero() {
        when(clock.instant()).thenReturn(FIXED_TIME);
        when(memberRepository.findByEmailIgnoreCase("priya@example.com"))
            .thenReturn(Optional.empty());

        Member saved = new Member("priya@example.com", "Priya", null, "ACTIVE", clock);
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        RegisterMemberRequest request = new RegisterMemberRequest("priya@example.com", "Priya", null);
        Member result = memberService.registerMember(request);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getSuspensionReason()).isNull();
        assertThat(result.getVersion()).isEqualTo(0);
    }

    @Test
    void test_ac3_duplicate_email_case_insensitive_returns_409() {
        Member existing = new Member("priya.k@example.com", "Priya K", null, "ACTIVE", clock);
        when(memberRepository.findByEmailIgnoreCase("Priya.K@example.com"))
            .thenReturn(Optional.of(existing));

        RegisterMemberRequest request = new RegisterMemberRequest("Priya.K@example.com", "Priya K", null);

        assertThatThrownBy(() -> memberService.registerMember(request))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS);
                assertThat(apiEx.getHttpStatus().value()).isEqualTo(409);
            });

        verify(memberRepository, never()).save(any());
    }

    @Test
    void test_ac4_email_casing_preserved() {
        when(clock.instant()).thenReturn(FIXED_TIME);
        when(memberRepository.findByEmailIgnoreCase("Priya.K@example.com"))
            .thenReturn(Optional.empty());

        Member saved = new Member("Priya.K@example.com", "Priya K", null, "ACTIVE", clock);
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        RegisterMemberRequest request = new RegisterMemberRequest("Priya.K@example.com", "Priya K", null);
        Member result = memberService.registerMember(request);

        assertThat(result.getEmail()).isEqualTo("Priya.K@example.com");
    }

    @Test
    void test_ac8_joined_at_reflects_frozen_clock() {
        Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        MemberService serviceWithFixedClock = new MemberService(memberRepository, fixedClock);

        when(memberRepository.findByEmailIgnoreCase("john@example.com"))
            .thenReturn(Optional.empty());

        Member saved = new Member("john@example.com", "John Doe", null, "ACTIVE", fixedClock);
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        RegisterMemberRequest request = new RegisterMemberRequest("john@example.com", "John Doe", null);
        Member result = serviceWithFixedClock.registerMember(request);

        assertThat(result.getJoinedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void test_ac9_whitespace_trimmed() {
        when(clock.instant()).thenReturn(FIXED_TIME);
        when(memberRepository.findByEmailIgnoreCase("john@example.com"))
            .thenReturn(Optional.empty());

        Member saved = new Member("john@example.com", "John Doe", null, "ACTIVE", clock);
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        RegisterMemberRequest request = new RegisterMemberRequest("john@example.com", "  John Doe  ", null);
        Member result = memberService.registerMember(request);

        assertThat(result.getFullName()).isEqualTo("John Doe");
    }
}
