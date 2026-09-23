package com.studio.booking.member.infrastructure;

import com.studio.booking.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    @Query("SELECT m FROM Member m WHERE LOWER(m.email) = LOWER(:email)")
    Optional<Member> findByEmailIgnoreCase(@Param("email") String email);
}
