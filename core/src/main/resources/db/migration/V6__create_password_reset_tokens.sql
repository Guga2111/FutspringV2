-- Password reset links (POST /auth/forgot-password). Only the SHA-256 of the token is stored, so a
-- database leak doesn't expose usable links. A token is valid while used_at is null and expires_at is
-- in the future; PasswordResetTokenCleanup deletes stale rows daily. Non-destructive.
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT                         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)                    NOT NULL UNIQUE,
    expires_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    used_at     TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
