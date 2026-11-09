package com.futspring.backend.domain.subscription;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** List prices. The amount actually charged is stored on each payment (a founder's locked price included). */
public final class SubscriptionPlans {

    /** Peladas with more members than this pay the large-group price. */
    public static final int LARGE_GROUP_MEMBERS = 40;

    private static final BigDecimal MONTHLY = new BigDecimal("19.90");
    private static final BigDecimal ANNUAL = new BigDecimal("199.00");
    private static final BigDecimal LARGE_MONTHLY = new BigDecimal("29.90");
    private static final BigDecimal LARGE_ANNUAL = new BigDecimal("299.00");

    private SubscriptionPlans() {
    }

    public static BigDecimal price(SubscriptionPlan plan, int memberCount) {
        boolean large = memberCount > LARGE_GROUP_MEMBERS;
        return switch (plan) {
            case MONTHLY -> large ? LARGE_MONTHLY : MONTHLY;
            case ANNUAL -> large ? LARGE_ANNUAL : ANNUAL;
        };
    }

    /** Money on the API: an exact two-decimal string ("19.90"). */
    public static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
