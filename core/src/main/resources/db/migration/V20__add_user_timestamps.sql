-- When each account was created and last changed (the platform admin's user list and its "new in the last 30 days").
-- Additive and nullable: the registration instant was only recorded since V16 (terms_accepted_at), so the accounts
-- created before stay null; both columns are filled by Hibernate from now on (User @CreationTimestamp/@UpdateTimestamp)
ALTER TABLE users ADD COLUMN created_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ NULL;

UPDATE users SET created_at = terms_accepted_at WHERE terms_accepted_at IS NOT NULL;
