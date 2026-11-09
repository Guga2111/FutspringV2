package com.futspring.backend.domain.subscription;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * {@link SubscriptionState#of} as a SQL condition over kind + valid_until, so a list can filter and count by the
 * effective state in the database. Must agree with SubscriptionState at every edge (now == validUntil is expired;
 * now == validUntil + GRACE is read-only), checked by PlatformControllerTest.
 */
public final class SubscriptionStatusFilter {

    private SubscriptionStatusFilter() {
    }

    public static Specification<PeladaSubscription> of(SubscriptionStatus status, Instant now) {
        return (root, query, cb) -> predicate(status, root, cb, now);
    }

    private static Predicate predicate(SubscriptionStatus status, Root<PeladaSubscription> root, CriteriaBuilder cb,
                                       Instant now) {
        Path<SubscriptionKind> kind = root.get("kind");
        Path<Instant> validUntil = root.get("validUntil");
        Instant graceStart = now.minus(SubscriptionState.GRACE);
        return switch (status) {
            case TRIAL -> cb.and(cb.equal(kind, SubscriptionKind.TRIAL), cb.greaterThan(validUntil, now));
            case ACTIVE -> cb.and(cb.equal(kind, SubscriptionKind.PAID), cb.greaterThan(validUntil, now));
            case PAST_DUE -> cb.and(cb.equal(kind, SubscriptionKind.PAID),
                    cb.lessThanOrEqualTo(validUntil, now), cb.greaterThan(validUntil, graceStart));
            case READ_ONLY -> cb.or(
                    cb.and(cb.equal(kind, SubscriptionKind.TRIAL),
                            cb.or(cb.isNull(validUntil), cb.lessThanOrEqualTo(validUntil, now))),
                    cb.and(cb.equal(kind, SubscriptionKind.PAID),
                            cb.or(cb.isNull(validUntil), cb.lessThanOrEqualTo(validUntil, graceStart))),
                    cb.equal(kind, SubscriptionKind.CANCELED));
            case PENDING -> cb.equal(kind, SubscriptionKind.PENDING);
            case EXEMPT -> cb.equal(kind, SubscriptionKind.EXEMPT);
        };
    }
}
