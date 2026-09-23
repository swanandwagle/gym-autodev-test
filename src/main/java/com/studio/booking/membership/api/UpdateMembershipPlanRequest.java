package com.studio.booking.membership.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

import java.util.HashSet;
import java.util.Set;

@Schema(description = "Update an existing membership plan")
public class UpdateMembershipPlanRequest {

    @Schema(description = "Plan name (unique case-insensitively). Omit to leave unchanged.", example = "Updated Plan Name")
    private String name;

    @Min(value = 1, message = "Class credits must be greater than 0")
    @Schema(description = "Tri-state: omit to leave unchanged, null to make unlimited, or value to set credit count.", example = "25")
    private Integer classCredits;

    @Schema(description = "Optimistic lock version. Required to prevent concurrent updates.", example = "0")
    private Long version;

    private final Set<String> presentFields = new HashSet<>();

    public UpdateMembershipPlanRequest() {}

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
        this.presentFields.add("name");
    }

    @JsonProperty("classCredits")
    public void setClassCredits(Integer classCredits) {
        this.classCredits = classCredits;
        this.presentFields.add("classCredits");
    }

    @JsonProperty("version")
    public void setVersion(Long version) {
        this.version = version;
        this.presentFields.add("version");
    }

    @JsonAnySetter
    public void handleUnknownFields(String fieldName, Object value) {
        this.presentFields.add(fieldName);
    }

    public String name() {
        return name;
    }

    public Integer classCredits() {
        return classCredits;
    }

    public Long version() {
        return version;
    }

    public boolean hasName() {
        return presentFields.contains("name");
    }

    public boolean hasClassCredits() {
        return presentFields.contains("classCredits");
    }

    public boolean hasVersion() {
        return presentFields.contains("version");
    }
}
