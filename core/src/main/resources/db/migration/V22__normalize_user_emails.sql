-- E-mails are stored trimmed and in lower case (User.normalizeEmail): register, login and forgot-password
-- normalize what the client sends. Production had no e-mail to change and no duplicate by case on 2026-11-11;
-- a duplicate would fail the UPDATE (users.email is unique) or the index, and the deploy with it.
UPDATE users SET email = LOWER(TRIM(email)) WHERE email <> LOWER(TRIM(email));

-- Two accounts can never differ only by case; also serves the exact e-mail match of the user search
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
