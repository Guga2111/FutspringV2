-- Subscription notices: a new notification category (BILLING) with three types, and the subscription's valid_until
-- the notice was about (snapshot: the text keeps its date if the subscription is renewed later). Additive,
-- non-destructive; existing rows keep valid_until null.
ALTER TABLE notifications ADD COLUMN valid_until TIMESTAMPTZ NULL;

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'DIARISTA_BUMPED', 'SESSION_CANCELED', 'ATTENDANCE_REMOVED_BY_ADMIN', 'ATTENDANCE_CONFIRMED_BY_ADMIN',
    'SESSION_REMINDER', 'SESSION_CREATED', 'DIARISTA_WINDOW_OPEN', 'TEAMS_SORTED', 'SESSION_FINISHED',
    'ADDED_TO_PELADA', 'REMOVED_FROM_PELADA', 'ADMIN_GRANTED', 'ADMIN_REVOKED', 'MENSALISTA_GRANTED',
    'MENSALISTA_REVOKED', 'PELADA_TRANSFERRED', 'AWARD_WON', 'PELADA_INVITATION', 'MEMBER_JOINED',
    'SUBSCRIPTION_ENDING', 'SUBSCRIPTION_PAST_DUE', 'SUBSCRIPTION_PIX_CLAIMED'));

ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_category_check;
ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_category_check
    CHECK (category IN ('SESSION', 'ATTENDANCE', 'MEMBERSHIP', 'AWARDS', 'BILLING'));

-- Backstop for SubscriptionNoticeJob: one notice per pelada, type and validity; the job's NOT EXISTS is the real guard
CREATE UNIQUE INDEX uq_notifications_subscription ON notifications (pelada_id, type, valid_until)
    WHERE type IN ('SUBSCRIPTION_ENDING', 'SUBSCRIPTION_PAST_DUE');
