package com.studio.booking.membership.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CreditLedgerResponse(
    @JsonProperty("credits_initial") Integer creditsInitial,
    @JsonProperty("credits_remaining") Integer creditsRemaining,
    @JsonProperty("reconciles") boolean reconciles,
    @JsonProperty("content") List<Entry> content
) {
    public record Entry(
        @JsonProperty("id") String id,
        @JsonProperty("delta") int delta,
        @JsonProperty("reason") String reason,
        @JsonProperty("balance_after") int balanceAfter,
        @JsonProperty("created_at") String createdAt
    ) {}
}
