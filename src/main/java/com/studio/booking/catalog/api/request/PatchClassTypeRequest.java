package com.studio.booking.catalog.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PatchClassTypeRequest {
    private Optional<String> name = Optional.empty();
    private Optional<String> description = Optional.empty();
    private Optional<Integer> durationMinutes = Optional.empty();
    private Optional<Integer> defaultCapacity = Optional.empty();
    private Optional<Long> version = Optional.empty();
    private final Map<String, Object> unknownFields = new HashMap<>();

    public Optional<String> getName() { return name; }
    public void setName(String value) { this.name = Optional.of(value); }

    public Optional<String> getDescription() { return description; }
    public void setDescription(String value) { this.description = Optional.of(value); }

    public Optional<Integer> getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer value) {
        if (value != null) {
            this.durationMinutes = Optional.of(value);
        }
    }

    public Optional<Integer> getDefaultCapacity() { return defaultCapacity; }
    public void setDefaultCapacity(Integer value) {
        if (value != null) {
            this.defaultCapacity = Optional.of(value);
        }
    }

    public Optional<Long> getVersion() { return version; }
    public void setVersion(Long value) { this.version = Optional.of(value); }

    public Map<String, Object> getUnknownFields() { return unknownFields; }

    @JsonAnySetter
    public void unknownField(String key, Object value) {
        this.unknownFields.put(key, value);
    }

    public boolean hasAnyUpdate() {
        return name.isPresent() || description.isPresent() ||
               durationMinutes.isPresent() || defaultCapacity.isPresent();
    }
}
