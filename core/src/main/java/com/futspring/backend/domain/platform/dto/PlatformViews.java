package com.futspring.backend.domain.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.futspring.backend.domain.pelada.PeladaRef;
import com.futspring.backend.domain.subscription.SubscriptionEventType;
import com.futspring.backend.domain.subscription.SubscriptionKind;
import com.futspring.backend.domain.subscription.SubscriptionPlan;
import com.futspring.backend.domain.subscription.SubscriptionStatus;
import com.futspring.backend.domain.user.dto.PublicUserDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Responses of the platform admin's panel (/platform/**). Money is an exact two-decimal string ("19.90"). */
public final class PlatformViews {

    private PlatformViews() {
    }

    public record Page<T>(List<T> items, long total) {}

    public record Overview(
            // every effective state, zeros included
            Map<SubscriptionStatus, Long> counts,
            // trials ending in the next 7 days, soonest first (up to 20); date = validUntil
            List<Attention> trialsEndingSoon,
            // creators waiting for their Pix to be confirmed, oldest first (up to 20); date = pixClaimedAt
            List<Attention> pixClaimed,
            String revenueThisMonth,
            // the last 12 months (billing zone), oldest first, the current one last
            List<MonthRevenue> revenueByMonth,
            // per month: payments covering today of peladas still paid, annual ones divided by 12
            String recurringEstimate,
            Users users) {}

    public record Attention(Long id, String name, Instant date) {}

    // month as yyyy-MM
    public record MonthRevenue(String month, String total) {}

    // accounts not deleted; last30Days by createdAt (null before V16, so never counted)
    public record Users(long total, long last30Days) {}

    public record PeladaItem(Long id, String name, PublicUserDTO creator, int memberCount, SubscriptionStatus status,
                             Instant validUntil, boolean founder, Instant pixClaimedAt, LocalDate lastSessionDate) {}

    public record PeladaDetail(PeladaInfo pelada, Subscription subscription, List<Payment> payments,
                               List<Event> events) {}

    public record PeladaInfo(Long id, String name, String image, PublicUserDTO creator, String creatorEmail,
                             int memberCount, LocalDate lastSessionDate) {}

    public record Subscription(SubscriptionKind kind, SubscriptionStatus status, boolean writable, Instant validUntil,
                               SubscriptionPlan plan, boolean founder, Instant pixClaimedAt,
                               SubscriptionPlan claimedPlan,
                               // list prices for the pelada's member count (the payment form's default amount)
                               List<Price> prices,
                               Instant createdAt, Instant updatedAt) {}

    public record Price(SubscriptionPlan plan, String amount) {}

    public record Payment(Long id, Long peladaId, String peladaName, Long payerId, String payerName, String amount,
                          LocalDate paidOn, SubscriptionPlan plan, Instant periodStart, Instant periodEnd,
                          PublicUserDTO recordedBy, String note, Instant voidedAt, String voidReason,
                          Instant createdAt) {}

    // sum = the payments of the period that aren't voided
    public record Payments(List<Payment> items, long total, String sum) {}

    public record Event(Long id, SubscriptionEventType type, PublicUserDTO actor, SubscriptionKind kindBefore,
                        SubscriptionKind kindAfter, Instant validUntilBefore, Instant validUntilAfter, String note,
                        Instant createdAt) {}

    public record UserItem(Long id, String username, String email, Instant createdAt, List<PeladaRef> peladasCreated,
                           long memberOf, Instant trialUsedAt,
                           @JsonProperty("isDeleted") boolean isDeleted) {}
}
