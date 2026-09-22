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
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private String status;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected WaitlistEntry() {}

    public WaitlistEntry(UUID sessionId, UUID memberId, int sequenceNo) {
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.status = "WAITING";
        this.sequenceNo = sequenceNo;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getMemberId() { return memberId; }
    public String getStatus() { return status; }
    public int getSequenceNo() { return sequenceNo; }
    public String getSkipReason() { return skipReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
