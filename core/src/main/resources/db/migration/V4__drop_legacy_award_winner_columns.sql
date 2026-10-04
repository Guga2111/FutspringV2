-- Drops daily_awards.puskas_winner_id and wiltball_winner_id. They are leftovers of the single-winner
-- awards, created in production by ddl-auto=update and never removed; no entity maps them (the
-- winners live in daily_award_puskas / daily_award_wiltball). V1 never creates them, so on any other
-- database this is a no-op.
--
-- Destructive (DROP COLUMN): take a Supabase backup before deploying. As a guard, the migration
-- aborts instead of dropping if any row still has a value in either column.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'daily_awards'
                 AND column_name = 'puskas_winner_id') THEN
        IF EXISTS (SELECT 1 FROM daily_awards WHERE puskas_winner_id IS NOT NULL) THEN
            RAISE EXCEPTION 'daily_awards.puskas_winner_id has values; refusing to drop it';
        END IF;
        ALTER TABLE daily_awards DROP COLUMN puskas_winner_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'daily_awards'
                 AND column_name = 'wiltball_winner_id') THEN
        IF EXISTS (SELECT 1 FROM daily_awards WHERE wiltball_winner_id IS NOT NULL) THEN
            RAISE EXCEPTION 'daily_awards.wiltball_winner_id has values; refusing to drop it';
        END IF;
        ALTER TABLE daily_awards DROP COLUMN wiltball_winner_id;
    END IF;
END $$;
