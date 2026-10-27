-- Player stars become per pelada and are set by its admins (PUT /peladas/{id}/players/{userId}/stars).
-- A member without a row counts as 3 stars. Every current member starts with the stars they had given
-- themselves (users.stars), so team sorting doesn't change on deploy. Non-destructive.
CREATE TABLE pelada_player_stars (
    id         BIGSERIAL PRIMARY KEY,
    pelada_id  BIGINT                         NOT NULL REFERENCES peladas (id) ON DELETE CASCADE,
    user_id    BIGINT                         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    stars      INTEGER                        NOT NULL CHECK (stars BETWEEN 1 AND 5),
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_pelada_player_stars UNIQUE (pelada_id, user_id)
);

CREATE INDEX idx_pelada_player_stars_user_id ON pelada_player_stars (user_id);

INSERT INTO pelada_player_stars (pelada_id, user_id, stars, updated_at)
SELECT m.pelada_id, m.user_id, LEAST(GREATEST(u.stars, 1), 5), now()
FROM pelada_members m
JOIN users u ON u.id = m.user_id;

-- The User entity no longer maps users.stars: new users get the default until a later migration drops it
ALTER TABLE users ALTER COLUMN stars SET DEFAULT 3;
