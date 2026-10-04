-- Backfill for "record match stats only for players on the two teams of each match".
--
-- Before that change submitResults wrote a player_match_stats row for every confirmed player of
-- the daily, so user_daily_stats.matches_played (and the rankings/stats sums built from it) counted
-- every match of the session. This removes the rows of players who were on neither team of the
-- match and rebuilds matches_played the same way finalizeDaily does.
--
-- Rows with goals or assists are kept, so no goal/assist is lost if one was typed for a player
-- outside the match. Postgres only. Take a Supabase backup before running (destructive).

BEGIN;

-- 1. Drop stat rows of players who were not on either team of the match
DELETE FROM player_match_stats pms
USING matches m
WHERE pms.match_id = m.id
  AND pms.goals = 0
  AND pms.assists = 0
  AND NOT EXISTS (
    SELECT 1 FROM team_players tp
    WHERE tp.user_id = pms.user_id
      AND tp.team_id IN (m.team1_id, m.team2_id)
  );

-- 2. Per session: matches played = stat rows left for that player in the daily's matches
UPDATE user_daily_stats uds
SET matches_played = (
  SELECT COUNT(*)
  FROM player_match_stats pms
  JOIN matches m ON m.id = pms.match_id
  WHERE m.daily_id = uds.daily_id
    AND pms.user_id = uds.user_id
);

-- 3. Pelada ranking: sum of the player's sessions in that pelada
UPDATE rankings r
SET matches_played = COALESCE((
  SELECT SUM(uds.matches_played)
  FROM user_daily_stats uds
  JOIN dailies d ON d.id = uds.daily_id
  WHERE d.pelada_id = r.pelada_id
    AND uds.user_id = r.user_id
), 0);

-- 4. Global stats: sum of all the player's sessions
UPDATE stats s
SET matches_played = COALESCE((
  SELECT SUM(uds.matches_played)
  FROM user_daily_stats uds
  WHERE uds.user_id = s.user_id
), 0);

COMMIT;
