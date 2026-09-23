package com.studio.booking.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "instructor")
public class Instructor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    private String bio;

    @Column(nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> specialties = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Instructor() {}

    public Instructor(String email, String name, String bio, List<String> specialties) {
        this.email = email;
        this.name = name;
        this.bio = bio;
        this.active = true;
        this.specialties = deduplicateSpecialties(specialties != null ? specialties : new ArrayList<>());
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getBio() { return bio; }
    public boolean isActive() { return active; }
    public List<String> getSpecialties() { return new ArrayList<>(specialties); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setEmail(String email) { this.email = email; }
    public void setName(String name) { this.name = name; }
    public void setBio(String bio) { this.bio = bio; }
    public void setSpecialties(List<String> specialties) {
        this.specialties = deduplicateSpecialties(specialties != null ? specialties : new ArrayList<>());
    }

    public void deactivate() {
        this.active = false;
    }

    private static List<String> deduplicateSpecialties(List<String> input) {
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String specialty : input) {
            String lower = specialty.toLowerCase();
            if (!seen.contains(lower)) {
                seen.add(lower);
                result.add(specialty);
            }
        }
        return result;
    }
}
