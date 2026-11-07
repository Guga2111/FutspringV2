package com.futspring.backend.domain.subscription;

// What was granted to a pelada; the effective state also depends on valid_until and now (SubscriptionState)
public enum SubscriptionKind {
    TRIAL,
    PENDING,
    PAID,
    EXEMPT,
    CANCELED
}
