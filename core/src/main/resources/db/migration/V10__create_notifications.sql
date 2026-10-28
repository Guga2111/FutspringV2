-- Notification center: one row per recipient with typed references (the text is rendered by the client and,
-- later, the e-mail), and the per-category e-mail preferences (a row only when the user changed the default).
-- Non-destructive. The ON DELETE clauses are a safety net: the services delete notifications explicitly with
-- their daily or pelada.
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(40)                 NOT NULL CHECK (type IN (
                     'DIARISTA_BUMPED', 'SESSION_CANCELED', 'ATTENDANCE_REMOVED_BY_ADMIN',
                     'ATTENDANCE_CONFIRMED_BY_ADMIN', 'SESSION_REMINDER',
                     'SESSION_CREATED', 'DIARISTA_WINDOW_OPEN', 'TEAMS_SORTED', 'SESSION_FINISHED',
                     'ADDED_TO_PELADA', 'REMOVED_FROM_PELADA', 'ADMIN_GRANTED', 'ADMIN_REVOKED',
                     'MENSALISTA_GRANTED', 'MENSALISTA_REVOKED', 'PELADA_TRANSFERRED',
                     'AWARD_WON')),
    pelada_id    BIGINT REFERENCES peladas (id) ON DELETE CASCADE,
    daily_id     BIGINT REFERENCES dailies (id) ON DELETE CASCADE,
    actor_id     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    award        VARCHAR(20) CHECK (award IN ('PUSKAS', 'WILTBALL', 'ARTILHEIRO', 'GARCOM')),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    read_at      TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_pelada_id ON notifications (pelada_id);
CREATE INDEX idx_notifications_daily_id ON notifications (daily_id);
CREATE INDEX idx_notifications_actor_id ON notifications (actor_id);
-- Backstop for the scheduled events; the job's INSERT ... WHERE NOT EXISTS is the real guard
CREATE UNIQUE INDEX uq_notifications_scheduled ON notifications (recipient_id, type, daily_id)
    WHERE type IN ('SESSION_REMINDER', 'DIARISTA_WINDOW_OPEN');

CREATE TABLE notification_preferences (
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category      VARCHAR(20) NOT NULL CHECK (category IN ('SESSION', 'ATTENDANCE', 'MEMBERSHIP', 'AWARDS')),
    email_enabled BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, category)
);
