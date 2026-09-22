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
@Table(name = "no_show_record")
public class NoShowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected NoShowRecord() {}

    public NoShowRecord(UUID bookingId, UUID memberId, UUID sessionId) {
        this.bookingId = bookingId;
        this.memberId = memberId;
        this.sessionId = sessionId;
        this.recordedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getMemberId() { return memberId; }
    public UUID getSessionId() { return sessionId; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
