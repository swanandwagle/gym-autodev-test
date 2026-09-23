package com.studio.booking.catalog.domain;

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
@Table(name = "class_session")
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "class_type_id", nullable = false)
    private UUID classTypeId;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "recurrence_id")
    private UUID recurrenceId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ClassSession() {}

    public ClassSession(UUID classTypeId, UUID instructorId, UUID roomId,
                        Instant startsAt, Instant endsAt, int capacity) {
        this(classTypeId, instructorId, roomId, startsAt, endsAt, capacity, null, Clock.systemUTC());
    }

    public ClassSession(UUID classTypeId, UUID instructorId, UUID roomId,
                        Instant startsAt, Instant endsAt, int capacity, Clock clock) {
        this(classTypeId, instructorId, roomId, startsAt, endsAt, capacity, null, clock);
    }

    public ClassSession(UUID classTypeId, UUID instructorId, UUID roomId,
                        Instant startsAt, Instant endsAt, int capacity, UUID recurrenceId, Clock clock) {
        this.classTypeId = classTypeId;
        this.instructorId = instructorId;
        this.roomId = roomId;
        this.recurrenceId = recurrenceId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.bookedCount = 0;
        this.status = "SCHEDULED";
        Instant now = clock.instant();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getClassTypeId() { return classTypeId; }
    public UUID getInstructorId() { return instructorId; }
    public UUID getRoomId() { return roomId; }
    public UUID getRecurrenceId() { return recurrenceId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getCapacity() { return capacity; }
    public int getBookedCount() { return bookedCount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setBookedCount(int bookedCount) { this.bookedCount = bookedCount; }

    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }
}
