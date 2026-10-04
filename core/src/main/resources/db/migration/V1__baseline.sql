-- Baseline: the schema Hibernate (ddl-auto) produced from the entities as of 2026-10-03,
-- dumped with pg_dump --schema-only from PostgreSQL 15.
-- Existing databases are marked as V1 by spring.flyway.baseline-on-migrate and never run this file;
-- it only builds the schema of an empty database.

CREATE TABLE public.dailies (
    daily_date date NOT NULL,
    is_finished boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    pelada_id bigint NOT NULL,
    champion_image character varying(255),
    daily_time character varying(255) NOT NULL,
    status character varying(255) NOT NULL
);

CREATE SEQUENCE public.dailies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.dailies_id_seq OWNED BY public.dailies.id;

CREATE TABLE public.daily_award_artilheiro (
    daily_award_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.daily_award_garcom (
    daily_award_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.daily_award_puskas (
    daily_award_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.daily_award_wiltball (
    daily_award_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.daily_awards (
    daily_id bigint NOT NULL,
    id bigint NOT NULL
);

CREATE SEQUENCE public.daily_awards_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.daily_awards_id_seq OWNED BY public.daily_awards.id;

CREATE TABLE public.daily_confirmed_players (
    daily_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.league_table_entries (
    draws integer NOT NULL,
    goals_against integer NOT NULL,
    goals_for integer NOT NULL,
    losses integer NOT NULL,
    points integer NOT NULL,
    "position" integer NOT NULL,
    wins integer NOT NULL,
    daily_id bigint NOT NULL,
    id bigint NOT NULL,
    team_id bigint NOT NULL
);

CREATE SEQUENCE public.league_table_entries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.league_table_entries_id_seq OWNED BY public.league_table_entries.id;

CREATE TABLE public.matches (
    team1score integer,
    team2score integer,
    daily_id bigint NOT NULL,
    id bigint NOT NULL,
    team1_id bigint NOT NULL,
    team2_id bigint NOT NULL,
    winner_id bigint
);

CREATE SEQUENCE public.matches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.matches_id_seq OWNED BY public.matches.id;

CREATE TABLE public.messages (
    id bigint NOT NULL,
    pelada_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    sent_at timestamp(6) without time zone NOT NULL,
    content character varying(500) NOT NULL
);

CREATE SEQUENCE public.messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.messages_id_seq OWNED BY public.messages.id;

CREATE TABLE public.pelada_admins (
    pelada_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.pelada_members (
    pelada_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.peladas (
    auto_create_daily_enabled boolean NOT NULL,
    duration real NOT NULL,
    number_of_teams integer NOT NULL,
    players_per_team integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    creator_id bigint,
    id bigint NOT NULL,
    address character varying(255),
    day_of_week character varying(255) NOT NULL,
    image character varying(255),
    name character varying(255) NOT NULL,
    reference character varying(255),
    time_of_day character varying(255) NOT NULL
);

CREATE SEQUENCE public.peladas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.peladas_id_seq OWNED BY public.peladas.id;

CREATE TABLE public.player_match_stats (
    assists integer NOT NULL,
    goals integer NOT NULL,
    id bigint NOT NULL,
    match_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE SEQUENCE public.player_match_stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.player_match_stats_id_seq OWNED BY public.player_match_stats.id;

CREATE TABLE public.rankings (
    assists integer NOT NULL,
    goals integer NOT NULL,
    matches_played integer NOT NULL,
    wins integer NOT NULL,
    id bigint NOT NULL,
    pelada_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE SEQUENCE public.rankings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.rankings_id_seq OWNED BY public.rankings.id;

CREATE TABLE public.stats (
    assists integer NOT NULL,
    goals integer NOT NULL,
    match_wins integer NOT NULL,
    matches_played integer NOT NULL,
    sessions_played integer NOT NULL,
    wins integer NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE SEQUENCE public.stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.stats_id_seq OWNED BY public.stats.id;

CREATE TABLE public.stats_puskas_dates (
    puskas_date date,
    stats_id bigint NOT NULL
);

CREATE TABLE public.team_players (
    team_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.teams (
    daily_id bigint NOT NULL,
    id bigint NOT NULL,
    color character varying(255),
    name character varying(255) NOT NULL
);

CREATE SEQUENCE public.teams_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.teams_id_seq OWNED BY public.teams.id;

CREATE TABLE public.user_daily_stats (
    assists integer NOT NULL,
    goals integer NOT NULL,
    matches_played integer NOT NULL,
    wins integer NOT NULL,
    won_session boolean NOT NULL,
    daily_id bigint NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE SEQUENCE public.user_daily_stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.user_daily_stats_id_seq OWNED BY public.user_daily_stats.id;

CREATE TABLE public.users (
    stars integer NOT NULL,
    id bigint NOT NULL,
    background_image character varying(255),
    email character varying(255) NOT NULL,
    image character varying(255),
    password character varying(255) NOT NULL,
    "position" character varying(255),
    username character varying(255) NOT NULL
);

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;

ALTER TABLE ONLY public.dailies ALTER COLUMN id SET DEFAULT nextval('public.dailies_id_seq'::regclass);

ALTER TABLE ONLY public.daily_awards ALTER COLUMN id SET DEFAULT nextval('public.daily_awards_id_seq'::regclass);

ALTER TABLE ONLY public.league_table_entries ALTER COLUMN id SET DEFAULT nextval('public.league_table_entries_id_seq'::regclass);

ALTER TABLE ONLY public.matches ALTER COLUMN id SET DEFAULT nextval('public.matches_id_seq'::regclass);

ALTER TABLE ONLY public.messages ALTER COLUMN id SET DEFAULT nextval('public.messages_id_seq'::regclass);

ALTER TABLE ONLY public.peladas ALTER COLUMN id SET DEFAULT nextval('public.peladas_id_seq'::regclass);

ALTER TABLE ONLY public.player_match_stats ALTER COLUMN id SET DEFAULT nextval('public.player_match_stats_id_seq'::regclass);

ALTER TABLE ONLY public.rankings ALTER COLUMN id SET DEFAULT nextval('public.rankings_id_seq'::regclass);

ALTER TABLE ONLY public.stats ALTER COLUMN id SET DEFAULT nextval('public.stats_id_seq'::regclass);

ALTER TABLE ONLY public.teams ALTER COLUMN id SET DEFAULT nextval('public.teams_id_seq'::regclass);

ALTER TABLE ONLY public.user_daily_stats ALTER COLUMN id SET DEFAULT nextval('public.user_daily_stats_id_seq'::regclass);

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);

ALTER TABLE ONLY public.dailies
    ADD CONSTRAINT dailies_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.daily_awards
    ADD CONSTRAINT daily_awards_daily_id_key UNIQUE (daily_id);

ALTER TABLE ONLY public.daily_awards
    ADD CONSTRAINT daily_awards_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.daily_confirmed_players
    ADD CONSTRAINT daily_confirmed_players_pkey PRIMARY KEY (daily_id, user_id);

ALTER TABLE ONLY public.league_table_entries
    ADD CONSTRAINT league_table_entries_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT matches_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pelada_admins
    ADD CONSTRAINT pelada_admins_pkey PRIMARY KEY (pelada_id, user_id);

ALTER TABLE ONLY public.pelada_members
    ADD CONSTRAINT pelada_members_pkey PRIMARY KEY (pelada_id, user_id);

ALTER TABLE ONLY public.peladas
    ADD CONSTRAINT peladas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.player_match_stats
    ADD CONSTRAINT player_match_stats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rankings
    ADD CONSTRAINT rankings_pelada_id_user_id_key UNIQUE (pelada_id, user_id);

ALTER TABLE ONLY public.rankings
    ADD CONSTRAINT rankings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.stats
    ADD CONSTRAINT stats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.stats
    ADD CONSTRAINT stats_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.team_players
    ADD CONSTRAINT team_players_pkey PRIMARY KEY (team_id, user_id);

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_daily_stats
    ADD CONSTRAINT user_daily_stats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_daily_stats
    ADD CONSTRAINT fk2ah1reg9weocwkf2l80upuw6o FOREIGN KEY (daily_id) REFERENCES public.dailies(id);

ALTER TABLE ONLY public.team_players
    ADD CONSTRAINT fk3bhsykltbdhsmmb61l2ml12h FOREIGN KEY (team_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk3ioil1py4fu8omd77sivakcwi FOREIGN KEY (team1_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk4ui4nnwntodh6wjvck53dbk9m FOREIGN KEY (sender_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.league_table_entries
    ADD CONSTRAINT fk50q1ol00cs1103sy6rxur0o6q FOREIGN KEY (daily_id) REFERENCES public.dailies(id);

ALTER TABLE ONLY public.daily_confirmed_players
    ADD CONSTRAINT fk58kp556w07qu4uxvsoln25tmt FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk5dx68xlrdt9e6gcx3uph5qgul FOREIGN KEY (daily_id) REFERENCES public.dailies(id);

ALTER TABLE ONLY public.team_players
    ADD CONSTRAINT fk5nyj3h53u6awg5n61w5tpf7td FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.pelada_admins
    ADD CONSTRAINT fk5pkp18719oudg2797j7vm318d FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.daily_awards
    ADD CONSTRAINT fk623alvc86qvq3tsvp2xmyhmt7 FOREIGN KEY (daily_id) REFERENCES public.dailies(id);

ALTER TABLE ONLY public.daily_award_artilheiro
    ADD CONSTRAINT fk7o2dkd87eugc8wpkhm9q5bmv9 FOREIGN KEY (daily_award_id) REFERENCES public.daily_awards(id);

ALTER TABLE ONLY public.daily_award_wiltball
    ADD CONSTRAINT fk85q6y6r6wbi29xyuef4nmp0kl FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.daily_award_garcom
    ADD CONSTRAINT fk9pity3nixt7jrrhykvaf2d87u FOREIGN KEY (daily_award_id) REFERENCES public.daily_awards(id);

ALTER TABLE ONLY public.league_table_entries
    ADD CONSTRAINT fkaju94wrwoviamg6dab6i8d443 FOREIGN KEY (team_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.pelada_members
    ADD CONSTRAINT fkavdx1x0jil1c20vvw8guk7koh FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.daily_award_artilheiro
    ADD CONSTRAINT fkb8kocdjoq55ohilah4d7mgr62 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fkbeel88lh2ksupphafotnqy7ry FOREIGN KEY (winner_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.stats_puskas_dates
    ADD CONSTRAINT fkc7mwp7errn9nt8d2b5atey0o FOREIGN KEY (stats_id) REFERENCES public.stats(id);

ALTER TABLE ONLY public.user_daily_stats
    ADD CONSTRAINT fkcfcgu3yeok0kmr5rl6kd475lt FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.rankings
    ADD CONSTRAINT fkcup4ei1jmensgunlbncpb5rnv FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fkdkphr8xw4l2dgywsnbdbe04d7 FOREIGN KEY (team2_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.daily_award_wiltball
    ADD CONSTRAINT fkfrq2ocemohn55a4bgouai8j66 FOREIGN KEY (daily_award_id) REFERENCES public.daily_awards(id);

ALTER TABLE ONLY public.daily_award_garcom
    ADD CONSTRAINT fkgu9jrucc75u4agi1ar3d5ql8e FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.dailies
    ADD CONSTRAINT fkhfkcvqi0e0ottwlncjpi5t7d2 FOREIGN KEY (pelada_id) REFERENCES public.peladas(id);

ALTER TABLE ONLY public.daily_award_puskas
    ADD CONSTRAINT fkiuan2s35iyvbsdo1pyb4dwer0 FOREIGN KEY (daily_award_id) REFERENCES public.daily_awards(id);

ALTER TABLE ONLY public.player_match_stats
    ADD CONSTRAINT fkk297a8jtsdibs8thlv8pt0qhw FOREIGN KEY (match_id) REFERENCES public.matches(id);

ALTER TABLE ONLY public.daily_award_puskas
    ADD CONSTRAINT fkk2uawyonbyh5jfl40756jmw2s FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.pelada_admins
    ADD CONSTRAINT fkmdspthuovr27tfq2pgy96bbt7 FOREIGN KEY (pelada_id) REFERENCES public.peladas(id);

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT fkmkro1hr7qkoi5mjyyscsjbex9 FOREIGN KEY (daily_id) REFERENCES public.dailies(id);

ALTER TABLE ONLY public.peladas
    ADD CONSTRAINT fkmwqa5nnv32465jgk86t07sw53 FOREIGN KEY (creator_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.daily_confirmed_players
    ADD CONSTRAINT fko9nak9l3mplu6m5475ays13tk FOREIGN KEY (daily_id) REFERENCES public.dailies(id);

ALTER TABLE ONLY public.player_match_stats
    ADD CONSTRAINT fkppu5g41pvqmeyy5vmingqgg6a FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.stats
    ADD CONSTRAINT fkprc49gj58occ6ng6my1rbqk7v FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.rankings
    ADD CONSTRAINT fkr956hfkmuvr7220436wetcn1h FOREIGN KEY (pelada_id) REFERENCES public.peladas(id);

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fkrdk1thhc3clmkv3kafv9ihmf FOREIGN KEY (pelada_id) REFERENCES public.peladas(id);

ALTER TABLE ONLY public.pelada_members
    ADD CONSTRAINT fkrryctl3rhjcqsu2fktd7af74 FOREIGN KEY (pelada_id) REFERENCES public.peladas(id);

