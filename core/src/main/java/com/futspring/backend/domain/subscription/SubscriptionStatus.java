package com.futspring.backend.domain.subscription;

// The effective state of a pelada's subscription, computed by SubscriptionState; never stored
public enum SubscriptionStatus {
    TRIAL(true),
    ACTIVE(true),
    PAST_DUE(true),
    READ_ONLY(false),
    PENDING(false),
    EXEMPT(true);

    private final boolean writable;

    SubscriptionStatus(boolean writable) {
        this.writable = writable;
    }

    /** Whether the pelada's sporting and financial data can be written in this state. */
    public boolean writable() {
        return writable;
    }
}
