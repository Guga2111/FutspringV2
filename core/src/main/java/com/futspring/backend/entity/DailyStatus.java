package com.futspring.backend.entity;

import java.util.Map;
import java.util.Set;

public enum DailyStatus {
    SCHEDULED,
    CONFIRMED,
    IN_COURSE,
    FINISHED,
    CANCELED;

    // Attendance and team changes are blocked in these statuses
    public static final Set<DailyStatus> LOCKED = Set.of(IN_COURSE, FINISHED, CANCELED);

    // Manual transitions (PUT /dailies/{id}/status); FINISHED is only reached through finalize
    private static final Map<DailyStatus, Set<DailyStatus>> TRANSITIONS = Map.of(
            SCHEDULED, Set.of(CONFIRMED, CANCELED),
            CONFIRMED, Set.of(IN_COURSE, CANCELED)
    );

    public boolean isLocked() {
        return LOCKED.contains(this);
    }

    public boolean canTransitionTo(DailyStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
