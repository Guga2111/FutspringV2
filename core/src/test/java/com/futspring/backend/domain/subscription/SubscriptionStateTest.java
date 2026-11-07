package com.futspring.backend.domain.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static com.futspring.backend.domain.subscription.SubscriptionState.of;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionStateTest {

    private static final Instant VALID_UNTIL = Instant.parse("2026-12-07T02:59:59Z");
    private static final Duration TICK = Duration.ofNanos(1);

    // an instant relative to valid_until
    private static Instant at(Duration offset) {
        return VALID_UNTIL.plus(offset);
    }

    @Test
    void trial_writesUntilValidUntil_thenIsReadOnlyWithoutGrace() {
        assertThat(of(SubscriptionKind.TRIAL, VALID_UNTIL, at(TICK.negated()))).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(of(SubscriptionKind.TRIAL, VALID_UNTIL, at(Duration.ZERO))).isEqualTo(SubscriptionStatus.READ_ONLY);
        assertThat(of(SubscriptionKind.TRIAL, VALID_UNTIL, at(Duration.ofDays(3)))).isEqualTo(SubscriptionStatus.READ_ONLY);
    }

    @Test
    void paid_isActive_thenPastDueFor7Days_thenReadOnly() {
        Duration grace = Duration.ofDays(7);
        assertThat(of(SubscriptionKind.PAID, VALID_UNTIL, at(TICK.negated()))).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(of(SubscriptionKind.PAID, VALID_UNTIL, at(Duration.ZERO))).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(of(SubscriptionKind.PAID, VALID_UNTIL, at(grace.minus(TICK)))).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(of(SubscriptionKind.PAID, VALID_UNTIL, at(grace))).isEqualTo(SubscriptionStatus.READ_ONLY);
    }

    @Test
    void pendingCanceledAndExempt_ignoreTheDate() {
        for (Instant now : new Instant[]{at(Duration.ofDays(-30)), at(Duration.ofDays(30))}) {
            assertThat(of(SubscriptionKind.PENDING, VALID_UNTIL, now)).isEqualTo(SubscriptionStatus.PENDING);
            assertThat(of(SubscriptionKind.CANCELED, VALID_UNTIL, now)).isEqualTo(SubscriptionStatus.READ_ONLY);
            assertThat(of(SubscriptionKind.EXEMPT, VALID_UNTIL, now)).isEqualTo(SubscriptionStatus.EXEMPT);
        }
        assertThat(of(SubscriptionKind.PENDING, null, at(Duration.ZERO))).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(of(SubscriptionKind.EXEMPT, null, at(Duration.ZERO))).isEqualTo(SubscriptionStatus.EXEMPT);
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionKind.class, names = {"TRIAL", "PAID"})
    void missingValidUntil_readsAsExpired(SubscriptionKind kind) {
        assertThat(of(kind, null, at(Duration.ZERO))).isEqualTo(SubscriptionStatus.READ_ONLY);
    }

    @Test
    void writableStates() {
        assertThat(SubscriptionStatus.values()).filteredOn(SubscriptionStatus::writable)
                .containsExactlyInAnyOrder(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE,
                        SubscriptionStatus.PAST_DUE, SubscriptionStatus.EXEMPT);
    }

    @Test
    void entityOverload_readsKindAndValidUntil() {
        var subscription = new PeladaSubscription(null, SubscriptionKind.PAID, VALID_UNTIL, VALID_UNTIL);
        assertThat(SubscriptionState.of(subscription, at(Duration.ofDays(1)))).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void prices_largeGroupAbove40Members() {
        assertThat(SubscriptionPlans.price(SubscriptionPlan.MONTHLY, 40)).isEqualByComparingTo(new BigDecimal("19.90"));
        assertThat(SubscriptionPlans.price(SubscriptionPlan.ANNUAL, 40)).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(SubscriptionPlans.price(SubscriptionPlan.MONTHLY, 41)).isEqualByComparingTo(new BigDecimal("29.90"));
        assertThat(SubscriptionPlans.price(SubscriptionPlan.ANNUAL, 41)).isEqualByComparingTo(new BigDecimal("299.00"));
    }
}
