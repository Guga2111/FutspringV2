package com.futspring.backend.domain.notification;

import static com.futspring.backend.domain.notification.NotificationCategory.*;

/**
 * Every kind of notification, with its category and whether it may go by e-mail and by Web Push (some stay in-app
 * even when the category is on; TEAMS_SORTED and ATTENDANCE_CONFIRMED_BY_ADMIN are pushed but not e-mailed). Adding a value means updating the CHECK constraint of notifications.type.
 */
public enum NotificationType {
    DIARISTA_BUMPED(ATTENDANCE, true, true),
    SESSION_CANCELED(ATTENDANCE, true, true),
    ATTENDANCE_REMOVED_BY_ADMIN(ATTENDANCE, true, true),
    ATTENDANCE_CONFIRMED_BY_ADMIN(ATTENDANCE, false, true),
    SESSION_REMINDER(ATTENDANCE, true, true),

    SESSION_CREATED(SESSION, true, true),
    DIARISTA_WINDOW_OPEN(SESSION, true, true),
    TEAMS_SORTED(SESSION, false, true),
    SESSION_FINISHED(SESSION, true, true),

    PELADA_INVITATION(MEMBERSHIP, true, true),
    MEMBER_JOINED(MEMBERSHIP, false, true),
    ADDED_TO_PELADA(MEMBERSHIP, true, true),
    REMOVED_FROM_PELADA(MEMBERSHIP, true, true),
    ADMIN_GRANTED(MEMBERSHIP, true, true),
    ADMIN_REVOKED(MEMBERSHIP, false, false),
    MENSALISTA_GRANTED(MEMBERSHIP, true, true),
    MENSALISTA_REVOKED(MEMBERSHIP, true, true),
    PELADA_TRANSFERRED(MEMBERSHIP, true, true),

    AWARD_WON(AWARDS, true, true),

    // to the creator, from SubscriptionNoticeJob (valid_until set)
    SUBSCRIPTION_ENDING(BILLING, true, true),
    SUBSCRIPTION_PAST_DUE(BILLING, true, true),
    // to the platform admins, on the creator's first "Já fiz o Pix"
    SUBSCRIPTION_PIX_CLAIMED(BILLING, true, true);

    private final NotificationCategory category;
    private final boolean emailable;
    private final boolean pushable;

    NotificationType(NotificationCategory category, boolean emailable, boolean pushable) {
        this.category = category;
        this.emailable = emailable;
        this.pushable = pushable;
    }

    public NotificationCategory category() {
        return category;
    }

    public boolean emailable() {
        return emailable;
    }

    public boolean pushable() {
        return pushable;
    }
}
