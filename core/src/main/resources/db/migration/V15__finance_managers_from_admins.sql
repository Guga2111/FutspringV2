-- Only designated responsáveis manage the finance now (admins and the creator only appoint them).
-- Every current admin and creator becomes a responsável, so no pelada loses who manages its money.
-- Only pairs that are members (finance_managers references pelada_members); non-destructive.
INSERT INTO finance_managers (pelada_id, user_id)
SELECT candidates.pelada_id, candidates.user_id
FROM (
    SELECT pelada_id, user_id FROM pelada_admins
    UNION
    SELECT id, creator_id FROM peladas WHERE creator_id IS NOT NULL
) candidates
JOIN pelada_members m ON m.pelada_id = candidates.pelada_id AND m.user_id = candidates.user_id
ON CONFLICT (pelada_id, user_id) DO NOTHING;
