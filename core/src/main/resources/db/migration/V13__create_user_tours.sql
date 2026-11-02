-- Guided tours of the web app a user has already seen (finished or closed), so a tour starts automatically only
-- once per user, on any device. One row per user and tour. Additive, non-destructive.
CREATE TABLE user_tours (
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tour         VARCHAR(30) NOT NULL CHECK (tour IN ('FINANCE_MANAGER', 'FINANCE_MEMBER')),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tour)
);
