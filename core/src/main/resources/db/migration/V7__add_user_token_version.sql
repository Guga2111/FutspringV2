-- Session revocation: every JWT carries the user's token_version (claim "ver") and is rejected once the
-- version changes. A password reset increments it, ending every existing session (web and chat CONNECT).
-- Existing rows get 0, the value assumed for tokens issued before this change (no "ver" claim), so
-- nobody is logged out by the deploy. Non-destructive.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
