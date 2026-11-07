-- Web Push: one row per browser/device subscription (the endpoint the push service gave it, and its keys).
-- Additive, no backfill. The ON DELETE CASCADE is only a safety net: the services delete subscriptions explicitly
-- (password reset/change and account deletion).
CREATE TABLE push_subscriptions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint         VARCHAR(1024) NOT NULL,
    p256dh           VARCHAR(200)  NOT NULL,
    auth             VARCHAR(50)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_success_at  TIMESTAMPTZ   NULL,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions (user_id);

-- Null = the category's default (NotificationCategory.defaultPush), so the existing rows, which only store the
-- e-mail choice, keep their meaning
ALTER TABLE notification_preferences ADD COLUMN push_enabled BOOLEAN NULL;
