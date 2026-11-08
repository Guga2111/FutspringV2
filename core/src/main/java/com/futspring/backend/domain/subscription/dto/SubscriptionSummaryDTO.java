package com.futspring.backend.domain.subscription.dto;

import com.futspring.backend.domain.subscription.SubscriptionStatus;

import java.time.Instant;

/** The subscription state a pelada's members see: the banner and which buttons are enabled. */
public record SubscriptionSummaryDTO(
        SubscriptionStatus status,
        // end of the trial or of the paid period (the grace runs 7 days past it); null for PENDING / EXEMPT
        Instant validUntil,
        // whether sporting and financial data can be written (the server re-checks every command)
        boolean writable) {

    public static SubscriptionSummaryDTO of(SubscriptionStatus status, Instant validUntil) {
        return new SubscriptionSummaryDTO(status, validUntil, status.writable());
    }
}
