package com.studio.booking.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_transaction")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private String reason;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditTransaction() {}

    public CreditTransaction(UUID membershipId, int delta, String reason, int balanceAfter) {
        this(membershipId, delta, reason, balanceAfter, Instant.now());
    }

    public CreditTransaction(UUID membershipId, int delta, String reason, int balanceAfter, Instant createdAt) {
        this.membershipId = membershipId;
        this.delta = delta;
        this.reason = reason;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMembershipId() { return membershipId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public int getDelta() { return delta; }
    public String getReason() { return reason; }
    public int getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
