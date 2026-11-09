-- When the account was deleted (anonymized). The row stays, so past matches, awards, rankings, chat and the
-- finance history of other people keep pointing at it; it shows as "Ex-jogador". Additive, non-destructive.
-- The anonymization keeps terms_version / terms_accepted_at (V16) as the record of the acceptance.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;
