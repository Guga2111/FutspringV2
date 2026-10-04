package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyDetailDTO.MatchDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.entity.LeagueTableEntry;
import com.futspring.backend.domain.daily.entity.Match;
import com.futspring.backend.domain.daily.entity.PlayerMatchStat;
import com.futspring.backend.domain.daily.entity.Team;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.LeagueTableEntryRepository;
import com.futspring.backend.domain.daily.repository.MatchRepository;
import com.futspring.backend.domain.daily.repository.PlayerMatchStatRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.entity.DailyAward;
import com.futspring.backend.domain.stats.entity.UserDailyStats;
import com.futspring.backend.domain.stats.repository.DailyAwardRepository;
import com.futspring.backend.domain.stats.repository.UserDailyStatsRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyResultsService {

    private static final Set<DailyStatus> RESULT_STATUSES = Set.of(DailyStatus.IN_COURSE, DailyStatus.FINISHED);
    private static final Set<DailyStatus> IMPORT_STATUSES = Set.of(DailyStatus.SCHEDULED, DailyStatus.CONFIRMED);

    private final FileUploadService fileUploadService;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final AggregateRebuildService aggregateRebuildService;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyDTOMapper dailyDTOMapper;
    private final DailyRepository dailyRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final LeagueTableEntryRepository leagueTableEntryRepository;
    private final DailyAwardRepository dailyAwardRepository;

    @Transactional
    public List<MatchDTO> submitResults(Long id, List<MatchResultDTO> results, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireAdmin(daily.getPelada(), caller);

        if (!RESULT_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Resultados só podem ser lançados em sessões em andamento ou finalizadas");
        }

        Map<Long, Team> teamMap = teamRepository.findByDailyWithPlayers(daily).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        List<Match> savedMatches = new ArrayList<>();
        Set<Long> submittedMatchIds = new HashSet<>();

        for (MatchResultDTO result : results) {
            if (result.getMatchId() != null && !submittedMatchIds.add(result.getMatchId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Partida repetida nos resultados");
            }
            Team t1 = teamMap.get(result.getTeam1Id());
            Team t2 = teamMap.get(result.getTeam2Id());
            if (t1 == null || t2 == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Os times informados não pertencem a esta sessão");
            }
            if (t1.getId().equals(t2.getId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Uma partida precisa de dois times diferentes");
            }

            // The match id comes from the body: it must belong to this daily (never another pelada's match)
            Match match = result.getMatchId() == null
                    ? Match.builder().daily(daily).build()
                    : matchRepository.findByIdAndDaily(result.getMatchId(), daily)
                            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Partida não encontrada nesta sessão"));

            Set<Long> team1Ids = t1.getPlayers().stream().map(User::getId).collect(Collectors.toSet());
            Set<Long> team2Ids = t2.getPlayers().stream().map(User::getId).collect(Collectors.toSet());

            // Validate player stats: players of these two teams only, sums within the team score
            Map<Long, MatchResultDTO.PlayerStatInputDTO> statsByUserId = new HashMap<>();
            if (result.getPlayerStats() != null) {
                int team1Goals = 0, team2Goals = 0, team1Assists = 0, team2Assists = 0;
                for (MatchResultDTO.PlayerStatInputDTO stat : result.getPlayerStats()) {
                    if (statsByUserId.put(stat.getUserId(), stat) != null) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "Jogador repetido nas estatísticas da partida");
                    }
                    if (team1Ids.contains(stat.getUserId())) {
                        team1Goals += stat.getGoals();
                        team1Assists += stat.getAssists();
                    } else if (team2Ids.contains(stat.getUserId())) {
                        team2Goals += stat.getGoals();
                        team2Assists += stat.getAssists();
                    } else {
                        throw new AppException(HttpStatus.BAD_REQUEST, "Há estatísticas de um jogador que não está nesta partida");
                    }
                }
                requireWithinScore(t1, "gols", team1Goals, result.getTeam1Score());
                requireWithinScore(t2, "gols", team2Goals, result.getTeam2Score());
                requireWithinScore(t1, "assistências", team1Assists, result.getTeam1Score());
                requireWithinScore(t2, "assistências", team2Assists, result.getTeam2Score());
            }

            match.setTeam1(t1);
            match.setTeam2(t2);
            match.setTeam1Score(result.getTeam1Score());
            match.setTeam2Score(result.getTeam2Score());
            match.setWinner(result.getTeam1Score() > result.getTeam2Score() ? t1
                    : result.getTeam2Score() > result.getTeam1Score() ? t2 : null);

            Match savedMatch = matchRepository.save(match);
            savedMatches.add(savedMatch);

            // Overwrite the match stats: one row per player of the two teams (single DELETE + batch INSERT),
            // so matchesPlayed counts the matches a player actually played
            playerMatchStatRepository.deleteByMatch(savedMatch);
            Set<User> matchPlayers = new LinkedHashSet<>(t1.getPlayers());
            matchPlayers.addAll(t2.getPlayers());
            List<PlayerMatchStat> statsToSave = new ArrayList<>();
            for (User player : matchPlayers) {
                MatchResultDTO.PlayerStatInputDTO input = statsByUserId.get(player.getId());
                statsToSave.add(PlayerMatchStat.builder()
                        .match(savedMatch)
                        .user(player)
                        .goals(input != null ? input.getGoals() : 0)
                        .assists(input != null ? input.getAssists() : 0)
                        .build());
            }
            playerMatchStatRepository.saveAll(statsToSave);
        }

        // The submitted list is the session's full set of matches: saved matches left out were removed by the admin
        Set<Long> savedMatchIds = savedMatches.stream().map(Match::getId).collect(Collectors.toSet());
        List<Match> removedMatches = matchRepository.findByDaily(daily).stream()
                .filter(m -> !savedMatchIds.contains(m.getId()))
                .toList();
        if (!removedMatches.isEmpty()) {
            playerMatchStatRepository.deleteByMatchIn(removedMatches);
            matchRepository.deleteAll(removedMatches);
        }

        List<Team> teams = new ArrayList<>(teamMap.values());
        if (daily.getStatus() == DailyStatus.FINISHED) {
            // Editing a finalized session: recompute its stats, awards, Ranking and Stats, keeping the voted awards
            Map<Long, User> sessionPlayers = sessionPlayers(teams);
            DailyAward award = dailyAwardRepository.findByDaily(daily).orElse(null);
            closeSession(daily, teams, savedMatches,
                    keepSessionPlayers(award != null ? award.getPuskasWinners() : null, sessionPlayers),
                    keepSessionPlayers(award != null ? award.getWiltballWinners() : null, sessionPlayers));
        } else {
            // Calculate and persist the live league table so it's visible before finalization
            persistLiveLeagueTable(daily, teams, savedMatches);
        }

        return savedMatches.stream()
                .map(m -> dailyDTOMapper.toMatchDTO(m, null))
                .toList();
    }

    private static void requireWithinScore(Team team, String what, int sum, int score) {
        if (sum > score) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "A soma de " + what + " dos jogadores do " + team.getName() + " (" + sum
                            + ") passa do placar do time (" + score + ")");
        }
    }

    private List<LeagueTableEntry> persistLiveLeagueTable(Daily daily, List<Team> teams, List<Match> matches) {
        leagueTableEntryRepository.deleteAll(leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily));

        Map<Long, LeagueTableEntry> leagueMap = new HashMap<>();
        for (Team team : teams) {
            leagueMap.put(team.getId(), LeagueTableEntry.builder()
                    .daily(daily)
                    .team(team)
                    .build());
        }

        for (Match match : matches) {
            Long t1Id = match.getTeam1().getId();
            Long t2Id = match.getTeam2().getId();
            int s1 = match.getTeam1Score() != null ? match.getTeam1Score() : 0;
            int s2 = match.getTeam2Score() != null ? match.getTeam2Score() : 0;

            LeagueTableEntry e1 = leagueMap.get(t1Id);
            LeagueTableEntry e2 = leagueMap.get(t2Id);

            if (e1 != null) { e1.setGoalsFor(e1.getGoalsFor() + s1); e1.setGoalsAgainst(e1.getGoalsAgainst() + s2); }
            if (e2 != null) { e2.setGoalsFor(e2.getGoalsFor() + s2); e2.setGoalsAgainst(e2.getGoalsAgainst() + s1); }

            if (s1 > s2) {
                if (e1 != null) { e1.setWins(e1.getWins() + 1); e1.setPoints(e1.getPoints() + 3); }
                if (e2 != null) { e2.setLosses(e2.getLosses() + 1); }
            } else if (s2 > s1) {
                if (e2 != null) { e2.setWins(e2.getWins() + 1); e2.setPoints(e2.getPoints() + 3); }
                if (e1 != null) { e1.setLosses(e1.getLosses() + 1); }
            } else {
                if (e1 != null) { e1.setDraws(e1.getDraws() + 1); e1.setPoints(e1.getPoints() + 1); }
                if (e2 != null) { e2.setDraws(e2.getDraws() + 1); e2.setPoints(e2.getPoints() + 1); }
            }
        }

        List<LeagueTableEntry> sortedEntries = leagueMap.values().stream()
                .sorted(Comparator
                        .comparingInt(LeagueTableEntry::getPoints).reversed()
                        .thenComparingInt((LeagueTableEntry e) -> e.getGoalsAgainst() - e.getGoalsFor()))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedEntries.size(); i++) {
            sortedEntries.get(i).setPosition(i + 1);
        }
        leagueTableEntryRepository.saveAll(sortedEntries);
        return sortedEntries;
    }

    @Transactional
    public void finalizeDaily(Long id, List<Long> puskasWinnerIds, List<Long> wiltballWinnerIds, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);

        if (!RESULT_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A sessão precisa estar em andamento ou finalizada para ser encerrada");
        }

        List<Match> matches = matchRepository.findByDaily(daily);
        if (matches.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Lance pelo menos um resultado antes de finalizar a sessão");
        }

        // Award winners must have played: players on the session's teams
        List<Team> teamsWithPlayers = teamRepository.findByDailyWithPlayers(daily);
        Map<Long, User> sessionPlayers = sessionPlayers(teamsWithPlayers);
        List<User> puskasWinners = resolveWinners(sessionPlayers, puskasWinnerIds, "Puskás");
        List<User> wiltballWinners = resolveWinners(sessionPlayers, wiltballWinnerIds, "Bola Murcha");

        closeSession(daily, teamsWithPlayers, matches, puskasWinners, wiltballWinners);
    }

    /**
     * Computes the session's UserDailyStats, league table and awards from its matches, rebuilds Ranking/Stats and
     * marks the daily FINISHED. The session's players are the players on its teams (the ones with match stats),
     * not the confirmed list, which can change after the teams are sorted.
     */
    private void closeSession(Daily daily, List<Team> teamsWithPlayers, List<Match> matches,
                              List<User> puskasWinners, List<User> wiltballWinners) {
        Pelada pelada = daily.getPelada();
        Map<Long, User> sessionPlayers = sessionPlayers(teamsWithPlayers);

        // Players of a previous finalize (or a previous award) must be rebuilt too, in case they dropped out
        DailyAward award = dailyAwardRepository.findByDaily(daily).orElse(DailyAward.builder().daily(daily).build());
        Set<User> affected = new LinkedHashSet<>(sessionPlayers.values());
        affected.addAll(award.getPuskasWinners());
        affected.addAll(award.getWiltballWinners());

        List<UserDailyStats> previousStats = userDailyStatsRepository.findByDaily(daily);
        previousStats.forEach(uds -> affected.add(uds.getUser()));
        userDailyStatsRepository.deleteAll(previousStats);

        Map<Long, UserDailyStats> statsMap = new HashMap<>();
        for (User player : sessionPlayers.values()) {
            statsMap.put(player.getId(), UserDailyStats.builder().daily(daily).user(player).build());
        }

        Map<Long, Set<Long>> teamPlayerIds = teamsWithPlayers.stream().collect(Collectors.toMap(
                Team::getId,
                t -> t.getPlayers().stream().map(User::getId).collect(Collectors.toSet())
        ));

        // Batch-load all match stats (no N+1)
        Map<Long, List<PlayerMatchStat>> statsByMatchId = playerMatchStatRepository.findByMatchInWithUser(matches).stream()
                .collect(Collectors.groupingBy(s -> s.getMatch().getId()));

        for (Match match : matches) {
            Long winnerId = match.getWinner() != null ? match.getWinner().getId() : null;
            Set<Long> winnerPlayerIds = winnerId != null
                    ? teamPlayerIds.getOrDefault(winnerId, Collections.emptySet())
                    : Collections.emptySet();
            for (PlayerMatchStat stat : statsByMatchId.getOrDefault(match.getId(), Collections.emptyList())) {
                UserDailyStats userStats = statsMap.get(stat.getUser().getId());
                if (userStats != null) {
                    userStats.setGoals(userStats.getGoals() + stat.getGoals());
                    userStats.setAssists(userStats.getAssists() + stat.getAssists());
                    userStats.setMatchesPlayed(userStats.getMatchesPlayed() + 1);
                    if (winnerPlayerIds.contains(stat.getUser().getId())) {
                        userStats.setWins(userStats.getWins() + 1);
                    }
                }
            }
        }

        List<LeagueTableEntry> sortedEntries = persistLiveLeagueTable(daily, teamsWithPlayers, matches);

        // Winning team: position 1 of the league table, none when tied on points and goal difference
        Team winningTeam = null;
        if (!sortedEntries.isEmpty()) {
            LeagueTableEntry first = sortedEntries.get(0);
            boolean tied = sortedEntries.size() > 1
                    && sortedEntries.get(1).getPoints() == first.getPoints()
                    && goalDiff(sortedEntries.get(1)) == goalDiff(first);
            if (!tied) {
                winningTeam = first.getTeam();
            }
        }
        Set<Long> winningPlayerIds = winningTeam != null
                ? teamPlayerIds.getOrDefault(winningTeam.getId(), Collections.emptySet())
                : Collections.emptySet();
        statsMap.values().forEach(uds -> uds.setWonSession(winningPlayerIds.contains(uds.getUser().getId())));
        userDailyStatsRepository.saveAll(statsMap.values());

        // Artilheiro (top scorer) and Garçom (top assists), ties share the award
        int maxGoals = statsMap.values().stream().mapToInt(UserDailyStats::getGoals).max().orElse(0);
        List<User> artilheiroWinners = maxGoals > 0
                ? statsMap.values().stream().filter(s -> s.getGoals() == maxGoals).map(UserDailyStats::getUser).collect(Collectors.toList())
                : new ArrayList<>();
        int maxAssists = statsMap.values().stream().mapToInt(UserDailyStats::getAssists).max().orElse(0);
        List<User> garcomWinners = maxAssists > 0
                ? statsMap.values().stream().filter(s -> s.getAssists() == maxAssists).map(UserDailyStats::getUser).collect(Collectors.toList())
                : new ArrayList<>();

        award.setPuskasWinners(puskasWinners);
        award.setWiltballWinners(wiltballWinners);
        award.setArtilheiroWinners(artilheiroWinners);
        award.setGarcomWinners(garcomWinners);
        dailyAwardRepository.save(award);

        aggregateRebuildService.rebuild(pelada, affected);

        daily.setStatus(DailyStatus.FINISHED);
        daily.setFinished(true);
        dailyRepository.save(daily);
    }

    private static Map<Long, User> sessionPlayers(List<Team> teamsWithPlayers) {
        Map<Long, User> players = new LinkedHashMap<>();
        teamsWithPlayers.forEach(t -> t.getPlayers().forEach(p -> players.put(p.getId(), p)));
        return players;
    }

    private static List<User> keepSessionPlayers(List<User> winners, Map<Long, User> sessionPlayers) {
        if (winners == null) {
            return new ArrayList<>();
        }
        return winners.stream().filter(w -> sessionPlayers.containsKey(w.getId())).collect(Collectors.toList());
    }

    private static List<User> resolveWinners(Map<Long, User> byId, List<Long> ids, String award) {
        if (ids == null) {
            return new ArrayList<>();
        }
        List<User> winners = new ArrayList<>();
        for (Long winnerId : new LinkedHashSet<>(ids)) {
            User winner = byId.get(winnerId);
            if (winner == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "O vencedor do " + award + " precisa ser um jogador de um dos times da sessão");
            }
            winners.add(winner);
        }
        return winners;
    }

    private static int goalDiff(LeagueTableEntry entry) {
        return entry.getGoalsFor() - entry.getGoalsAgainst();
    }

    @Transactional
    public DailyListItemDTO uploadChampionImage(Long id, MultipartFile file, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireAdmin(daily.getPelada(), caller);

        if (daily.getStatus() != DailyStatus.FINISHED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A foto dos campeões só pode ser enviada em sessões finalizadas");
        }

        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(daily.getChampionImage());
        daily.setChampionImage(filename);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public void populateFromMessage(Long id, PopulateDailyRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);

        if (!IMPORT_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Só é possível importar em sessões agendadas ou confirmadas");
        }

        // Every player once, team colors unique, every player a member of the pelada
        List<Long> allUserIds = new ArrayList<>();
        Set<Long> seenUsers = new HashSet<>();
        Set<String> seenColors = new HashSet<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO team : request.getTeams()) {
            if (!seenColors.add(team.getColorName().toLowerCase(Locale.ROOT))) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Time repetido: " + team.getColorName());
            }
            for (PopulateDailyRequestDTO.ParsedPlayerDTO player : team.getPlayers()) {
                if (!seenUsers.add(player.getUserId())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Um jogador aparece em mais de um time");
                }
                allUserIds.add(player.getUserId());
            }
        }

        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Set<Long> memberIds = pelada.getMembers().stream().map(User::getId).collect(Collectors.toSet());
        for (Long uid : allUserIds) {
            if (!userMap.containsKey(uid) || !memberIds.contains(uid)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Um dos jogadores não é membro desta pelada");
            }
        }

        // Wipe existing matches (stats first), then teams, then attendance
        List<Match> existingMatches = matchRepository.findByDaily(daily);
        if (!existingMatches.isEmpty()) {
            playerMatchStatRepository.deleteByMatchIn(existingMatches);
            matchRepository.deleteAll(existingMatches);
        }
        dailyTeamManagementService.clearTeams(daily);
        daily.getConfirmedPlayers().clear();

        Map<String, Team> colorToTeam = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.getTeams()) {
            Team team = Team.builder()
                    .daily(daily)
                    .name(parsedTeam.getColorName())
                    .color(parsedTeam.getColorHex())
                    .build();
            parsedTeam.getPlayers().forEach(p -> team.getPlayers().add(userMap.get(p.getUserId())));
            colorToTeam.put(parsedTeam.getColorName().toLowerCase(Locale.ROOT), teamRepository.save(team));
        }

        allUserIds.forEach(uid -> daily.getConfirmedPlayers().add(userMap.get(uid)));
        dailyRepository.save(daily);

        List<Match> savedMatches = new ArrayList<>();
        if (request.getMatches() != null) {
            for (PopulateDailyRequestDTO.ParsedMatchDTO parsedMatch : request.getMatches()) {
                Team t1 = colorToTeam.get(parsedMatch.getTeam1ColorName().toLowerCase(Locale.ROOT));
                Team t2 = colorToTeam.get(parsedMatch.getTeam2ColorName().toLowerCase(Locale.ROOT));
                if (t1 == null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Time desconhecido: " + parsedMatch.getTeam1ColorName());
                }
                if (t2 == null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Time desconhecido: " + parsedMatch.getTeam2ColorName());
                }
                if (t1.getId().equals(t2.getId())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Uma partida precisa de dois times diferentes: " + parsedMatch.getTeam1ColorName());
                }
                Team winner = parsedMatch.getTeam1Score() > parsedMatch.getTeam2Score() ? t1
                        : parsedMatch.getTeam2Score() > parsedMatch.getTeam1Score() ? t2 : null;
                savedMatches.add(matchRepository.save(Match.builder()
                        .daily(daily)
                        .team1(t1)
                        .team2(t2)
                        .team1Score(parsedMatch.getTeam1Score())
                        .team2Score(parsedMatch.getTeam2Score())
                        .winner(winner)
                        .build()));
            }
        }

        // Distribute each player's totals over the matches of their team
        Map<Long, Map<Long, Map<Long, Integer>>> teamGoalsDist = new HashMap<>();
        Map<Long, Map<Long, Map<Long, Integer>>> teamAssistsDist = new HashMap<>();
        Map<Long, Team> playerTeamMap = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.getTeams()) {
            Team team = colorToTeam.get(parsedTeam.getColorName().toLowerCase(Locale.ROOT));
            List<Match> teamMatches = savedMatches.stream()
                    .filter(m -> m.getTeam1().getId().equals(team.getId()) || m.getTeam2().getId().equals(team.getId()))
                    .toList();
            teamGoalsDist.put(team.getId(), distributeStats(parsedTeam.getPlayers(), teamMatches, team, true));
            teamAssistsDist.put(team.getId(), distributeStats(parsedTeam.getPlayers(), teamMatches, team, false));
            parsedTeam.getPlayers().forEach(p -> playerTeamMap.put(p.getUserId(), team));
        }

        // PlayerMatchStats only for players on the two teams playing each match
        List<PlayerMatchStat> allStats = new ArrayList<>();
        for (Match match : savedMatches) {
            Set<User> matchPlayers = new LinkedHashSet<>(match.getTeam1().getPlayers());
            matchPlayers.addAll(match.getTeam2().getPlayers());
            for (User player : matchPlayers) {
                Team playerTeam = playerTeamMap.get(player.getId());
                int goals = 0;
                int assists = 0;
                if (playerTeam != null) {
                    goals = teamGoalsDist.get(playerTeam.getId())
                            .getOrDefault(match.getId(), Map.of()).getOrDefault(player.getId(), 0);
                    assists = teamAssistsDist.get(playerTeam.getId())
                            .getOrDefault(match.getId(), Map.of()).getOrDefault(player.getId(), 0);
                }
                allStats.add(PlayerMatchStat.builder()
                        .match(match)
                        .user(player)
                        .goals(goals)
                        .assists(assists)
                        .build());
            }
        }
        playerMatchStatRepository.saveAll(allStats);

        daily.setStatus(DailyStatus.IN_COURSE);
        dailyRepository.save(daily);
    }

    private Map<Long, Map<Long, Integer>> distributeStats(
            List<PopulateDailyRequestDTO.ParsedPlayerDTO> players,
            List<Match> teamMatches,
            Team team,
            boolean isGoals) {

        // remaining[userId] = totalGoals or totalAssists to distribute
        Map<Long, Integer> remaining = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedPlayerDTO p : players) {
            int count = isGoals ? p.getTotalGoals() : p.getTotalAssists();
            if (count > 0) {
                remaining.put(p.getUserId(), count);
            }
        }

        // result[matchId][userId] = count assigned
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (Match m : teamMatches) {
            Map<Long, Integer> matchMap = new HashMap<>();
            for (PopulateDailyRequestDTO.ParsedPlayerDTO p : players) {
                matchMap.put(p.getUserId(), 0);
            }
            result.put(m.getId(), matchMap);
        }

        for (Match match : teamMatches) {
            int slots = match.getTeam1().getId().equals(team.getId())
                    ? match.getTeam1Score()
                    : match.getTeam2Score();
            if (slots <= 0) continue;

            // Weighted pool: userId repeated by remaining count
            List<Long> pool = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : remaining.entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    pool.add(entry.getKey());
                }
            }
            Collections.shuffle(pool);

            int assigned = 0;
            for (Long userId : pool) {
                if (assigned >= slots) break;
                result.get(match.getId()).merge(userId, 1, Integer::sum);
                remaining.merge(userId, -1, Integer::sum);
                if (remaining.getOrDefault(userId, 0) <= 0) remaining.remove(userId);
                assigned++;
            }
        }

        return result;
    }

    /**
     * Deletes every result of the daily: award, UserDailyStats, matches with their stats and the league table.
     * Returns the players whose Ranking/Stats must be rebuilt (empty unless the daily was finalized);
     * the caller rebuilds them after deleting the daily. Called by DailyService.deleteDailyData.
     */
    Set<User> clearResults(Daily daily) {
        dailyAwardRepository.findByDaily(daily).ifPresent(dailyAwardRepository::delete);

        List<UserDailyStats> userDailyStats = userDailyStatsRepository.findByDaily(daily);
        Set<User> affected = daily.isFinished()
                ? userDailyStats.stream().map(UserDailyStats::getUser).collect(Collectors.toCollection(LinkedHashSet::new))
                : new LinkedHashSet<>();
        userDailyStatsRepository.deleteAll(userDailyStats);

        leagueTableEntryRepository.deleteAll(leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily));

        List<Match> matches = matchRepository.findByDaily(daily);
        if (!matches.isEmpty()) {
            playerMatchStatRepository.deleteByMatchIn(matches);
            matchRepository.deleteAll(matches);
        }
        return affected;
    }

    private Daily findDaily(Long id) {
        return dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }
}
