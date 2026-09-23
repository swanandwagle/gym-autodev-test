package com.studio.booking.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(nullable = false)
    private String status;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Member() {}

    public Member(String email, String fullName, String phone, String status, Clock clock) {
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.status = status;
        this.suspensionReason = null;
        this.joinedAt = Instant.now(clock);
        this.createdAt = Instant.now(clock);
        this.updatedAt = Instant.now(clock);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public String getSuspensionReason() { return suspensionReason; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
