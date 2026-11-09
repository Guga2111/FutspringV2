package com.futspring.backend.domain.subscription.dto;

import com.futspring.backend.domain.subscription.SubscriptionPlan;
import com.futspring.backend.domain.subscription.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The pelada's subscription as its creator sees it (the Assinatura area). Money is a two-decimal string. */
public record PeladaSubscriptionDTO(
        SubscriptionStatus status,
        // end of the trial or of the paid period; null for PENDING / EXEMPT / CANCELED
        Instant validUntil,
        SubscriptionPlan plan,
        boolean founder,
        // the creator said they paid (cleared when the platform admin records the payment)
        Instant pixClaimedAt,
        SubscriptionPlan claimedPlan,
        // the full name of who receives the Pix; null when the platform has no Pix configured
        String pixRecipient,
        // list prices for the pelada's member count, each with its BR Code (null without Pix)
        List<PlanOffer> plans,
        // the latest payments, voided ones left out, newest first
        List<Payment> payments) {

    public record PlanOffer(SubscriptionPlan plan, String amount, String pixCode) {}

    public record Payment(LocalDate paidOn, String amount, SubscriptionPlan plan, Instant periodEnd) {}
}
