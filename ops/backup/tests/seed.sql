-- Deliberately synthetic. Exercise real migrations, money, sporting stats and sequences.
INSERT INTO users(email,password,username) VALUES ('synthetic@invalid','synthetic-hash','Sintético');
INSERT INTO peladas(auto_create_daily_enabled,duration,number_of_teams,players_per_team,created_at,creator_id,name,day_of_week,time_of_day)
VALUES(false,1,2,5,now(),1,'Pelada sintética','MONDAY','19:00');
INSERT INTO pelada_members VALUES(1,1);
INSERT INTO dailies(daily_date,is_finished,created_at,pelada_id,daily_time,status)
VALUES(current_date,false,now(),1,'19:00','SCHEDULED');
INSERT INTO stats(assists,goals,match_wins,matches_played,sessions_played,wins,user_id) VALUES(2,3,1,4,1,1,1);
INSERT INTO finance_movements(pelada_id,kind,amount,obligation_delta,effective_date,method,description,created_by,created_at)
VALUES(1,'OPENING',100.25,0,current_date,'CASH','Saldo sintético',1,now());
INSERT INTO finance_movements(pelada_id,kind,amount,obligation_delta,effective_date,method,description,created_by,created_at)
VALUES(1,'EXPENSE',-10.05,0,current_date,'CASH','Saída sintética',1,now());
