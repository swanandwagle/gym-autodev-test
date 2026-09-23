package com.studio.booking.catalog.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PatchInstructorRequest {
    private Optional<String> email = Optional.empty();
    private Optional<String> name = Optional.empty();
    private Optional<String> bio = Optional.empty();
    @Size(max = 20, message = "specialties must contain at most 20 items")
    private Optional<List<@Size(max = 40, message = "each specialty must be at most 40 characters") String>> specialties = Optional.empty();
    private Optional<Long> version = Optional.empty();
    private final Map<String, Object> unknownFields = new HashMap<>();

    public Optional<String> getEmail() { return email; }
    public void setEmail(String value) { this.email = Optional.of(value); }

    public Optional<String> getName() { return name; }
    public void setName(String value) { this.name = Optional.of(value); }

    public Optional<String> getBio() { return bio; }
    public void setBio(String value) { this.bio = Optional.of(value); }

    public Optional<List<String>> getSpecialties() { return specialties; }
    public void setSpecialties(List<String> value) { this.specialties = Optional.of(value); }

    public Optional<Long> getVersion() { return version; }
    public void setVersion(Long value) { this.version = Optional.of(value); }

    public Map<String, Object> getUnknownFields() { return unknownFields; }

    @JsonAnySetter
    public void unknownField(String key, Object value) {
        this.unknownFields.put(key, value);
    }

    public boolean hasAnyUpdate() {
        return email.isPresent() || name.isPresent() || bio.isPresent() || specialties.isPresent();
    }
}
