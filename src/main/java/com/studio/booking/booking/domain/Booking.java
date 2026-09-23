package com.studio.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String source;

    @Column(name = "cancellation_type")
    private String cancellationType;

    @Column(name = "checked_in_by")
    private String checkedInBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Booking() {}

    public Booking(UUID memberId, UUID sessionId, String source) {
        this.memberId = memberId;
        this.sessionId = sessionId;
        this.status = "BOOKED";
        this.source = source;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getMembershipId() { return membershipId; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getCancellationType() { return cancellationType; }
    public String getCheckedInBy() { return checkedInBy; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setMembershipId(UUID membershipId) { this.membershipId = membershipId; }
    public void setStatus(String status) { this.status = status; }
    public void setCancellationType(String cancellationType) { this.cancellationType = cancellationType; }
    public void setCheckedInBy(String checkedInBy) { this.checkedInBy = checkedInBy; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
