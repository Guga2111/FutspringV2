-- Which version of the terms of use and privacy policy each account accepted at registration, and when.
-- Nullable: accounts created before this migration have no recorded acceptance. Additive, no backfill.
ALTER TABLE users ADD COLUMN terms_version VARCHAR(20);
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMP(6) WITH TIME ZONE;
