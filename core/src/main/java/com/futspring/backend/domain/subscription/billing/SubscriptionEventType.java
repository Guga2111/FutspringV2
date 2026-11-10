package com.futspring.backend.domain.subscription.billing;

// Values mirror the CHECK on subscription_events.type (V19)
public enum SubscriptionEventType {
    CREATED,
    PAYMENT_RECORDED,
    PAYMENT_VOIDED,
    VALID_UNTIL_SET,
    DAYS_EXTENDED,
    EXEMPTED,
    CANCELED,
    FOUNDER_CHANGED,
    PIX_CLAIMED
}
