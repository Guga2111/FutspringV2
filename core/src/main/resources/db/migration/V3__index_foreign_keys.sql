-- Indexes on every foreign key and lookup column. V1 only had primary keys and unique constraints,
-- so membership checks, per-daily loads, cascading deletes and FK checks scanned whole tables.
-- Non-destructive. IF NOT EXISTS keeps it safe to run on a database that already has some of them.

-- Peladas / membership (the join tables' primary keys already lead with pelada_id / daily_id / team_id)
CREATE INDEX IF NOT EXISTS idx_peladas_creator_id ON peladas (creator_id);
CREATE INDEX IF NOT EXISTS idx_peladas_auto_create ON peladas (auto_create_daily_enabled);
CREATE INDEX IF NOT EXISTS idx_pelada_members_user_id ON pelada_members (user_id);
CREATE INDEX IF NOT EXISTS idx_pelada_admins_user_id ON pelada_admins (user_id);

-- Dailies
CREATE INDEX IF NOT EXISTS idx_dailies_pelada_id_daily_date ON dailies (pelada_id, daily_date);
CREATE INDEX IF NOT EXISTS idx_daily_confirmed_players_user_id ON daily_confirmed_players (user_id);

-- Teams and matches
CREATE INDEX IF NOT EXISTS idx_teams_daily_id ON teams (daily_id);
CREATE INDEX IF NOT EXISTS idx_team_players_user_id ON team_players (user_id);
CREATE INDEX IF NOT EXISTS idx_matches_daily_id ON matches (daily_id);
CREATE INDEX IF NOT EXISTS idx_matches_team1_id ON matches (team1_id);
CREATE INDEX IF NOT EXISTS idx_matches_team2_id ON matches (team2_id);
CREATE INDEX IF NOT EXISTS idx_matches_winner_id ON matches (winner_id);
CREATE INDEX IF NOT EXISTS idx_player_match_stats_match_id ON player_match_stats (match_id);
CREATE INDEX IF NOT EXISTS idx_player_match_stats_user_id ON player_match_stats (user_id);
CREATE INDEX IF NOT EXISTS idx_league_table_entries_daily_id ON league_table_entries (daily_id);
CREATE INDEX IF NOT EXISTS idx_league_table_entries_team_id ON league_table_entries (team_id);

-- Aggregates
CREATE INDEX IF NOT EXISTS idx_user_daily_stats_daily_id ON user_daily_stats (daily_id);
CREATE INDEX IF NOT EXISTS idx_user_daily_stats_user_id ON user_daily_stats (user_id);
CREATE INDEX IF NOT EXISTS idx_rankings_user_id ON rankings (user_id);
CREATE INDEX IF NOT EXISTS idx_stats_puskas_dates_stats_id ON stats_puskas_dates (stats_id);

-- Awards (the winner join tables have no primary key)
CREATE INDEX IF NOT EXISTS idx_daily_award_puskas_award_id ON daily_award_puskas (daily_award_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_puskas_user_id ON daily_award_puskas (user_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_wiltball_award_id ON daily_award_wiltball (daily_award_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_wiltball_user_id ON daily_award_wiltball (user_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_artilheiro_award_id ON daily_award_artilheiro (daily_award_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_artilheiro_user_id ON daily_award_artilheiro (user_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_garcom_award_id ON daily_award_garcom (daily_award_id);
CREATE INDEX IF NOT EXISTS idx_daily_award_garcom_user_id ON daily_award_garcom (user_id);

-- Chat history is read newest first per pelada
CREATE INDEX IF NOT EXISTS idx_messages_pelada_id_sent_at ON messages (pelada_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages (sender_id);
