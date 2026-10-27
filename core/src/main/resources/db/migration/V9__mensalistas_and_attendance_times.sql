-- Mensalistas (monthly members with priority on attendance) and diaristas (pay per session).
-- Peladas get a time zone (the diarista confirmation window opens 24 h before the session start in that zone)
-- and a mensalista limit; attendance rows get the instant they were confirmed (a mensalista who confirms into a
-- full session takes the spot of the diarista confirmed last); bumps are recorded to warn the diarista.
-- Non-destructive: every new column has a default, so the previous API version keeps inserting during a deploy.
ALTER TABLE peladas ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo';
ALTER TABLE peladas ADD COLUMN max_mensalistas INTEGER NOT NULL DEFAULT 0 CHECK (max_mensalistas >= 0);

CREATE TABLE pelada_mensalistas (
    id         BIGSERIAL PRIMARY KEY,
    pelada_id  BIGINT                         NOT NULL REFERENCES peladas (id) ON DELETE CASCADE,
    user_id    BIGINT                         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_pelada_mensalistas UNIQUE (pelada_id, user_id)
);
CREATE INDEX idx_pelada_mensalistas_user_id ON pelada_mensalistas (user_id);

-- Table and PK (daily_id, user_id) unchanged; legacy rows get the session's creation instant
ALTER TABLE daily_confirmed_players ADD COLUMN confirmed_at TIMESTAMP(6) WITH TIME ZONE;
UPDATE daily_confirmed_players c SET confirmed_at = COALESCE(d.created_at, now())
FROM dailies d WHERE d.id = c.daily_id;
ALTER TABLE daily_confirmed_players ALTER COLUMN confirmed_at SET NOT NULL;
ALTER TABLE daily_confirmed_players ALTER COLUMN confirmed_at SET DEFAULT now();

CREATE TABLE daily_attendance_bumps (
    id           BIGSERIAL PRIMARY KEY,
    daily_id     BIGINT                      NOT NULL REFERENCES dailies (id) ON DELETE CASCADE,
    user_id      BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- bumped diarista
    bumped_by_id BIGINT                      NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- mensalista
    bumped_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    seen_at      TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX idx_daily_attendance_bumps_daily_id ON daily_attendance_bumps (daily_id);
CREATE INDEX idx_daily_attendance_bumps_user_unseen ON daily_attendance_bumps (user_id) WHERE seen_at IS NULL;
CREATE INDEX idx_daily_attendance_bumps_bumped_by_id ON daily_attendance_bumps (bumped_by_id);
