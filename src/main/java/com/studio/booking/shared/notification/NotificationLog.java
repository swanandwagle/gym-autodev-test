package com.studio.booking.shared.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String channel;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationLog() {}

    public NotificationLog(UUID memberId, String eventType, String channel,
                           String payload, String triggeredBy) {
        this(memberId, eventType, channel, payload, triggeredBy, Clock.systemUTC());
    }

    public NotificationLog(UUID memberId, String eventType, String channel,
                           String payload, String triggeredBy, Clock clock) {
        this.memberId = memberId;
        this.eventType = eventType;
        this.channel = channel;
        this.payload = payload;
        this.triggeredBy = triggeredBy;
        Instant now = clock.instant();
        this.sentAt = now;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public String getEventType() { return eventType; }
    public String getChannel() { return channel; }
    public String getPayload() { return payload; }
    public String getTriggeredBy() { return triggeredBy; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
