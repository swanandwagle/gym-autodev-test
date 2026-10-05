package com.studio.booking.membership.domain;

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
@Table(name = "membership")
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean unlimited;

    @Column(name = "credits_initial")
    private Integer creditsInitial;

    @Column(name = "credits_remaining")
    private Integer creditsRemaining;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Membership() {}

    public Membership(UUID memberId, UUID planId, String status, boolean unlimited,
                      Integer creditsInitial, Integer creditsRemaining, Instant startsAt, Instant expiresAt, Clock clock) {
        this.memberId = memberId;
        this.planId = planId;
        this.status = status;
        this.unlimited = unlimited;
        this.creditsInitial = creditsInitial;
        this.creditsRemaining = creditsRemaining;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
        Instant now = Instant.now(clock);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Membership(UUID memberId, UUID planId, Integer creditsRemaining, Clock clock) {
        Instant now = Instant.now(clock);
        this.memberId = memberId;
        this.planId = planId;
        this.status = "ACTIVE";
        this.unlimited = false;
        this.creditsInitial = creditsRemaining;
        this.creditsRemaining = creditsRemaining;
        this.startsAt = now;
        this.expiresAt = now.plusSeconds(31536000);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public UUID getPlanId() { return planId; }
    public String getStatus() { return status; }
    public boolean isUnlimited() { return unlimited; }
    public Integer getCreditsInitial() { return creditsInitial; }
    public Integer getCreditsRemaining() { return creditsRemaining; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setCreditsRemaining(Integer creditsRemaining) {
        this.creditsRemaining = creditsRemaining;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setUnlimited(boolean unlimited) {
        this.unlimited = unlimited;
    }
}
