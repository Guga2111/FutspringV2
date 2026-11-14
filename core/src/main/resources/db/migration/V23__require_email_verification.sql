ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN email_verification_hash VARCHAR(64) UNIQUE;
ALTER TABLE users ADD COLUMN email_verification_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN email_verification_sent_at TIMESTAMPTZ;
-- Existing email ownership was never checked. Revoke old access and refresh sessions at rollout.
UPDATE users SET token_version = token_version + 1 WHERE deleted_at IS NULL;
