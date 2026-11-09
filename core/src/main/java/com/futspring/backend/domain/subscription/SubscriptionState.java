package com.futspring.backend.domain.subscription;

import java.time.Duration;
import java.time.Instant;

/** The effective state of a subscription at an instant. Pure: no job changes the stored kind when a date passes. */
public final class SubscriptionState {

    /** A paid subscription keeps writing for this long after valid_until (a trial has no grace). */
    public static final Duration GRACE = Duration.ofDays(7);

    private SubscriptionState() {
    }

    public static SubscriptionStatus of(PeladaSubscription subscription, Instant now) {
        return of(subscription.getKind(), subscription.getValidUntil(), now);
    }

    public static SubscriptionStatus of(SubscriptionKind kind, Instant validUntil, Instant now) {
        return switch (kind) {
            case TRIAL -> isBefore(now, validUntil) ? SubscriptionStatus.TRIAL : SubscriptionStatus.READ_ONLY;
            case PAID -> {
                if (isBefore(now, validUntil)) {
                    yield SubscriptionStatus.ACTIVE;
                }
                yield validUntil != null && now.isBefore(validUntil.plus(GRACE))
                        ? SubscriptionStatus.PAST_DUE : SubscriptionStatus.READ_ONLY;
            }
            case PENDING -> SubscriptionStatus.PENDING;
            case CANCELED -> SubscriptionStatus.READ_ONLY;
            case EXEMPT -> SubscriptionStatus.EXEMPT;
        };
    }

    // valid_until is required for TRIAL/PAID (DB CHECK); a missing one reads as expired
    private static boolean isBefore(Instant now, Instant validUntil) {
        return validUntil != null && now.isBefore(validUntil);
    }
}
