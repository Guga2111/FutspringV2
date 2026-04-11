package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyDetailDTO.*;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.dto.MatchResultDTO;
import com.futspring.backend.dto.PopulateDailyRequestDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyService {

    private final FileUploadService fileUploadService;
    private final UserAuthenticationHelper userAuthHelper;
    private final DailyAttendanceService dailyAttendanceService;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyDTOMapper dailyDTOMapper;
    private final DailyRepository dailyRepository;
    private final PeladaRepository peladaRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final LeagueTableEntryRepository leagueTableEntryRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final StatsRepository statsRepository;
    private final RankingRepository rankingRepository;

    @Transactional
    public DailyListItemDTO createDaily(Long peladaId, CreateDailyRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can create dailies");
        }

        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(request.getDailyDate())
                .dailyTime(request.getDailyTime())
                .build();

        Daily saved = dailyRepository.save(daily);
        return DailyListItemDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DailyListItemDTO> getDailiesForPelada(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        return dailyRepository.findByPeladaOrderByDailyDateDesc(pelada).stream()
                .map(DailyListItemDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public DailyListItemDTO confirmAttendance(Long id, String currentUserEmail) {
        return dailyAttendanceService.confirmAttendance(id, currentUserEmail);
    }

    @Transactional
    public DailyListItemDTO disconfirmAttendance(Long id, String currentUserEmail) {
        return dailyAttendanceService.disconfirmAttendance(id, currentUserEmail);
    }

    private static final java.util.Map<String, java.util.Set<String>> VALID_TRANSITIONS = java.util.Map.of(
            "SCHEDULED", java.util.Set.of("CONFIRMED", "CANCELED"),
            "CONFIRMED", java.util.Set.of("IN_COURSE", "CANCELED")
    );

    @Transactional
    public DailyListItemDTO updateStatus(Long id, String newStatus, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can update daily status");
        }

        java.util.Set<String> allowed = VALID_TRANSITIONS.getOrDefault(daily.getStatus(), java.util.Set.of());
        if (!allowed.contains(newStatus)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid status transition from " + daily.getStatus() + " to " + newStatus);
        }

        daily.setStatus(newStatus);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public List<DailyDetailDTO.TeamDTO> sortTeams(Long id, String currentUserEmail) {
        return dailyTeamManagementService.sortTeams(id, currentUserEmail);
    }

    @Transactional
    public List<DailyDetailDTO.TeamDTO> swapPlayers(Long id, Long player1Id, Long player2Id, String currentUserEmail) {
        return dailyTeamManagementService.swapPlayers(id, player1Id, player2Id, currentUserEmail);
    }

    @Transactional
    public List<MatchDTO> submitResults(Long id, List<MatchResultDTO> results, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can submit results");
        }

        if (!"IN_COURSE".equals(daily.getStatus()) && !"FINISHED".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Results can only be submitted for dailies with status IN_COURSE or FINISHED");
        }

        java.util.Map<Long, Team> teamMap = teamRepository.findByDaily(daily).stream()
                .collect(java.util.stream.Collectors.toMap(Team::getId, t -> t));

        java.util.List<Match> savedMatches = new java.util.ArrayList<>();

        for (MatchResultDTO result : results) {
            Team t1 = teamMap.get(result.getTeam1Id());
            Team t2 = teamMap.get(result.getTeam2Id());
            if (t1 == null || t2 == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid team IDs in results");
            }

            Match match;
            if (result.getMatchId() != null) {
                match = matchRepository.findById(result.getMatchId()).orElse(null);
            } else {
                match = null;
            }

            if (match == null) {
                match = Match.builder().daily(daily).team1(t1).team2(t2).build();
            }

            match.setTeam1(t1);
            match.setTeam2(t2);
            match.setTeam1Score(result.getTeam1Score());
            match.setTeam2Score(result.getTeam2Score());

            Team winner = null;
            if (result.getTeam1Score() > result.getTeam2Score()) {
                winner = t1;
            } else if (result.getTeam2Score() > result.getTeam1Score()) {
                winner = t2;
            }
            match.setWinner(winner);

            Match savedMatch = matchRepository.save(match);
            savedMatches.add(savedMatch);

            // Save/overwrite player stats
            java.util.Map<Long, MatchResultDTO.PlayerStatInputDTO> statsByUserId = new java.util.HashMap<>();
            if (result.getPlayerStats() != null) {
                for (MatchResultDTO.PlayerStatInputDTO stat : result.getPlayerStats()) {
                    statsByUserId.put(stat.getUserId(), stat);
                }

                // Validate: sum of player goals/assists per team must not exceed team score
                java.util.Set<Long> team1Ids = t1.getPlayers().stream().map(User::getId).collect(Collectors.toSet());
                java.util.Set<Long> team2Ids = t2.getPlayers().stream().map(User::getId).collect(Collectors.toSet());
                int team1GoalSum = 0, team2GoalSum = 0, team1AssistSum = 0, team2AssistSum = 0;
                for (MatchResultDTO.PlayerStatInputDTO stat : statsByUserId.values()) {
                    if (team1Ids.contains(stat.getUserId())) {
                        team1GoalSum += stat.getGoals();
                        team1AssistSum += stat.getAssists();
                    } else if (team2Ids.contains(stat.getUserId())) {
                        team2GoalSum += stat.getGoals();
                        team2AssistSum += stat.getAssists();
                    }
                }
                if (team1GoalSum > result.getTeam1Score())
                    throw new AppException(HttpStatus.BAD_REQUEST,
                        t1.getName() + " player goals (" + team1GoalSum + ") exceed team score (" + result.getTeam1Score() + ")");
                if (team2GoalSum > result.getTeam2Score())
                    throw new AppException(HttpStatus.BAD_REQUEST,
                        t2.getName() + " player goals (" + team2GoalSum + ") exceed team score (" + result.getTeam2Score() + ")");
                if (team1AssistSum > result.getTeam1Score())
                    throw new AppException(HttpStatus.BAD_REQUEST,
                        t1.getName() + " player assists (" + team1AssistSum + ") exceed team score (" + result.getTeam1Score() + ")");
                if (team2AssistSum > result.getTeam2Score())
                    throw new AppException(HttpStatus.BAD_REQUEST,
                        t2.getName() + " player assists (" + team2AssistSum + ") exceed team score (" + result.getTeam2Score() + ")");
            }

            // Delete existing stats for this match (single DELETE statement)
            playerMatchStatRepository.deleteByMatch(savedMatch);

            // Save stats for all confirmed players (batch INSERT)
            java.util.List<PlayerMatchStat> statsToSave = new java.util.ArrayList<>();
            for (User player : daily.getConfirmedPlayers()) {
                MatchResultDTO.PlayerStatInputDTO input = statsByUserId.get(player.getId());
                int goals = input != null ? input.getGoals() : 0;
                int assists = input != null ? input.getAssists() : 0;
                statsToSave.add(PlayerMatchStat.builder()
                        .match(savedMatch)
                        .user(player)
                        .goals(goals)
                        .assists(assists)
                        .build());
            }
            playerMatchStatRepository.saveAll(statsToSave);
        }

        // Calculate and persist live league table so it's visible before finalization
        java.util.List<Team> allTeams = teamRepository.findByDailyWithPlayers(daily);
        java.util.List<Match> allMatches = matchRepository.findByDaily(daily);
        persistLiveLeagueTable(daily, allTeams, allMatches);

        return savedMatches.stream()
                .map(m -> MatchDTO.builder()
                        .id(m.getId())
                        .team1Id(m.getTeam1().getId())
                        .team1Name(m.getTeam1().getName())
                        .team2Id(m.getTeam2().getId())
                        .team2Name(m.getTeam2().getName())
                        .team1Score(m.getTeam1Score())
                        .team2Score(m.getTeam2Score())
                        .winnerId(m.getWinner() != null ? m.getWinner().getId() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private java.util.List<LeagueTableEntry> persistLiveLeagueTable(Daily daily, java.util.List<Team> teams, java.util.List<Match> matches) {
        leagueTableEntryRepository.deleteAll(leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily));

        java.util.Map<Long, LeagueTableEntry> leagueMap = new java.util.HashMap<>();
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

        java.util.List<LeagueTableEntry> sortedEntries = leagueMap.values().stream()
                .sorted(java.util.Comparator
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
    public DailyDetailDTO finalizeDaily(Long id, List<Long> puskasWinnerIds, List<Long> wiltballWinnerIds, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can finalize dailies");
        }

        if (!"IN_COURSE".equals(daily.getStatus()) && !"FINISHED".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Daily must be IN_COURSE or FINISHED to finalize");
        }

        java.util.List<Match> matches = matchRepository.findByDaily(daily);
        if (matches.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Daily must have at least one match result to finalize");
        }

        // Validate award winners are confirmed players
        java.util.Set<User> confirmedPlayers = daily.getConfirmedPlayers();

        List<Long> resolvedPuskasIds = puskasWinnerIds != null ? puskasWinnerIds : new ArrayList<>();
        List<Long> resolvedWiltballIds = wiltballWinnerIds != null ? wiltballWinnerIds : new ArrayList<>();

        List<User> puskasWinners = resolvedPuskasIds.stream()
                .map(pid -> confirmedPlayers.stream()
                        .filter(u -> u.getId().equals(pid))
                        .findFirst()
                        .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Puskas winner must be a confirmed player")))
                .collect(Collectors.toList());

        List<User> wiltballWinners = resolvedWiltballIds.stream()
                .map(wid -> confirmedPlayers.stream()
                        .filter(u -> u.getId().equals(wid))
                        .findFirst()
                        .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Wiltball winner must be a confirmed player")))
                .collect(Collectors.toList());

        // Compute UserDailyStats per confirmed player
        userDailyStatsRepository.deleteAll(userDailyStatsRepository.findByDaily(daily));

        java.util.Map<Long, UserDailyStats> statsMap = new java.util.HashMap<>();
        for (User player : confirmedPlayers) {
            statsMap.put(player.getId(), UserDailyStats.builder()
                    .daily(daily)
                    .user(player)
                    .build());
        }

        // Batch-load teams with players and all match stats (eliminates N+1 queries)
        java.util.List<Team> teamsWithPlayers = teamRepository.findByDailyWithPlayers(daily);
        Map<Long, Set<Long>> teamPlayerIds = teamsWithPlayers.stream().collect(Collectors.toMap(
                Team::getId,
                t -> t.getPlayers().stream().map(User::getId).collect(Collectors.toSet())
        ));

        java.util.List<PlayerMatchStat> allMatchStats = playerMatchStatRepository.findByMatchInWithUser(matches);
        Map<Long, List<PlayerMatchStat>> statsByMatchId = allMatchStats.stream()
                .collect(Collectors.groupingBy(s -> s.getMatch().getId()));

        for (Match match : matches) {
            List<PlayerMatchStat> matchStats = statsByMatchId.getOrDefault(match.getId(), Collections.emptyList());
            Long winnerId = match.getWinner() != null ? match.getWinner().getId() : null;
            Set<Long> winnerPlayerIds = winnerId != null ? teamPlayerIds.getOrDefault(winnerId, Collections.emptySet()) : Collections.emptySet();
            for (PlayerMatchStat stat : matchStats) {
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

        // Compute LeagueTableEntry per team
        java.util.List<LeagueTableEntry> sortedEntries = persistLiveLeagueTable(daily, teamsWithPlayers, matches);

        // Determine winning team (position 1 from league table, null if tied)
        Team winningTeam = null;
        if (!sortedEntries.isEmpty()) {
            LeagueTableEntry first = sortedEntries.get(0);
            boolean tied = sortedEntries.size() > 1 &&
                    sortedEntries.get(1).getPoints() == first.getPoints() &&
                    (sortedEntries.get(1).getGoalsFor() - sortedEntries.get(1).getGoalsAgainst()) ==
                    (first.getGoalsFor() - first.getGoalsAgainst());
            if (!tied) {
                winningTeam = first.getTeam();
            }
        }

        // Set wonSession on each player's UserDailyStats, then persist
        for (User player : confirmedPlayers) {
            UserDailyStats uds = statsMap.get(player.getId());
            if (uds != null) {
                boolean isWinner = winningTeam != null && teamPlayerIds.getOrDefault(winningTeam.getId(), Collections.emptySet()).contains(player.getId());
                uds.setWonSession(isWinner);
            }
        }
        userDailyStatsRepository.saveAll(statsMap.values());

        // Compute Artilheiro (top goal scorer) and Garçom (top assister) winners
        int maxGoals = statsMap.values().stream().mapToInt(UserDailyStats::getGoals).max().orElse(0);
        java.util.List<User> artilheiroWinners = maxGoals > 0
            ? statsMap.values().stream()
                .filter(s -> s.getGoals() == maxGoals)
                .map(UserDailyStats::getUser)
                .collect(Collectors.toList())
            : new java.util.ArrayList<>();

        int maxAssists = statsMap.values().stream().mapToInt(UserDailyStats::getAssists).max().orElse(0);
        java.util.List<User> garcomWinners = maxAssists > 0
            ? statsMap.values().stream()
                .filter(s -> s.getAssists() == maxAssists)
                .map(UserDailyStats::getUser)
                .collect(Collectors.toList())
            : new java.util.ArrayList<>();

        // Create or update DailyAward
        DailyAward award = dailyAwardRepository.findByDaily(daily).orElse(DailyAward.builder().daily(daily).build());
        award.setPuskasWinners(puskasWinners);
        award.setWiltballWinners(wiltballWinners);
        award.setArtilheiroWinners(artilheiroWinners);
        award.setGarcomWinners(garcomWinners);
        dailyAwardRepository.save(award);

        // Full-rebuild Ranking (per-pelada) and Stats (global) from UserDailyStats aggregates — batch queries
        List<User> playerList = new ArrayList<>(confirmedPlayers);

        // Load existing records in bulk (2 queries)
        Map<Long, Ranking> rankingMap = rankingRepository.findByPeladaAndUserIn(pelada, playerList).stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), r -> r));
        Map<Long, Stats> globalStatsMap = statsRepository.findByUserIn(playerList).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        // Batch aggregate queries (2 queries total)
        Map<Long, Object[]> rankingAgg = userDailyStatsRepository
                .aggregateRankingByUsersAndPelada(playerList, pelada).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
        Map<Long, Object[]> statsAgg = userDailyStatsRepository
                .aggregateStatsByUsers(playerList).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        // Batch puskas dates (1 query)
        Map<Long, List<LocalDate>> puskasByUser = new HashMap<>();
        dailyAwardRepository.findPuskasDatesByUsers(playerList).forEach(row -> {
            Long uid = (Long) row[0];
            LocalDate date = (LocalDate) row[1];
            puskasByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(date);
        });

        List<Ranking> rankingsToSave = new ArrayList<>();
        List<Stats> statsToSave = new ArrayList<>();

        for (User player : confirmedPlayers) {
            Long uid = player.getId();

            Ranking ranking = rankingMap.getOrDefault(uid, Ranking.builder().pelada(pelada).user(player).build());
            Object[] ra = rankingAgg.get(uid);
            if (ra != null) {
                ranking.setGoals(((Number) ra[1]).intValue());
                ranking.setAssists(((Number) ra[2]).intValue());
                ranking.setMatchesPlayed(((Number) ra[3]).intValue());
                ranking.setWins(((Number) ra[4]).intValue());
            }
            rankingsToSave.add(ranking);

            Stats stats = globalStatsMap.getOrDefault(uid, Stats.builder().user(player).build());
            Object[] sa = statsAgg.get(uid);
            if (sa != null) {
                stats.setGoals(((Number) sa[1]).intValue());
                stats.setAssists(((Number) sa[2]).intValue());
                stats.setMatchesPlayed(((Number) sa[3]).intValue());
                stats.setMatchWins(((Number) sa[4]).intValue());
                stats.setSessionsPlayed(((Number) sa[5]).intValue());
                stats.setWins(((Number) sa[6]).intValue());
            }
            stats.setPuskasDates(new ArrayList<>(puskasByUser.getOrDefault(uid, Collections.emptyList())));
            statsToSave.add(stats);
        }

        rankingRepository.saveAll(rankingsToSave);
        statsRepository.saveAll(statsToSave);

        // Mark daily as FINISHED
        daily.setStatus("FINISHED");
        daily.setFinished(true);
        dailyRepository.save(daily);

        return getDailyDetail(id, currentUserEmail);
    }

    @Transactional
    public void deleteDaily(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can delete dailies");
        }

        // Delete awards
        dailyAwardRepository.findByDaily(daily).ifPresent(dailyAwardRepository::delete);

        // Collect affected players and finalized state before deleting UserDailyStats
        boolean wasFinished = daily.isFinished();
        List<UserDailyStats> userDailyStatsList = userDailyStatsRepository.findByDaily(daily);
        List<User> affectedPlayers = userDailyStatsList.stream()
                .map(UserDailyStats::getUser)
                .distinct()
                .toList();

        // Delete userDailyStats
        userDailyStatsRepository.deleteAll(userDailyStatsList);

        // Recalculate Ranking + Stats if the deleted daily was finished
        if (wasFinished && !affectedPlayers.isEmpty()) {
            Map<Long, Ranking> rankingMap = rankingRepository
                    .findByPeladaAndUserIn(pelada, affectedPlayers).stream()
                    .collect(Collectors.toMap(r -> r.getUser().getId(), r -> r));
            Map<Long, Stats> globalStatsMap = statsRepository
                    .findByUserIn(affectedPlayers).stream()
                    .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

            Map<Long, Object[]> rankingAgg = userDailyStatsRepository
                    .aggregateRankingByUsersAndPelada(affectedPlayers, pelada).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
            Map<Long, Object[]> statsAgg = userDailyStatsRepository
                    .aggregateStatsByUsers(affectedPlayers).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

            Map<Long, List<LocalDate>> puskasByUser = new HashMap<>();
            dailyAwardRepository.findPuskasDatesByUsers(affectedPlayers).forEach(row -> {
                Long uid = (Long) row[0];
                LocalDate date = (LocalDate) row[1];
                puskasByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(date);
            });

            List<Ranking> rankingsToSave = new ArrayList<>();
            List<Stats> statsToSave = new ArrayList<>();

            for (User player : affectedPlayers) {
                Long uid = player.getId();

                Ranking ranking = rankingMap.getOrDefault(uid,
                        Ranking.builder().pelada(pelada).user(player).build());
                Object[] ra = rankingAgg.get(uid);
                ranking.setGoals(ra != null ? ((Number) ra[1]).intValue() : 0);
                ranking.setAssists(ra != null ? ((Number) ra[2]).intValue() : 0);
                ranking.setMatchesPlayed(ra != null ? ((Number) ra[3]).intValue() : 0);
                ranking.setWins(ra != null ? ((Number) ra[4]).intValue() : 0);
                rankingsToSave.add(ranking);

                Stats stats = globalStatsMap.getOrDefault(uid,
                        Stats.builder().user(player).build());
                Object[] sa = statsAgg.get(uid);
                stats.setGoals(sa != null ? ((Number) sa[1]).intValue() : 0);
                stats.setAssists(sa != null ? ((Number) sa[2]).intValue() : 0);
                stats.setMatchesPlayed(sa != null ? ((Number) sa[3]).intValue() : 0);
                stats.setMatchWins(sa != null ? ((Number) sa[4]).intValue() : 0);
                stats.setSessionsPlayed(sa != null ? ((Number) sa[5]).intValue() : 0);
                stats.setWins(sa != null ? ((Number) sa[6]).intValue() : 0);
                stats.setPuskasDates(new ArrayList<>(
                        puskasByUser.getOrDefault(uid, Collections.emptyList())));
                statsToSave.add(stats);
            }

            rankingRepository.saveAll(rankingsToSave);
            statsRepository.saveAll(statsToSave);
        }

        // Delete player match stats and matches
        List<Match> matches = matchRepository.findByDaily(daily);
        for (Match match : matches) {
            List<PlayerMatchStat> stats = playerMatchStatRepository.findByMatch(match);
            playerMatchStatRepository.deleteAll(stats);
        }
        matchRepository.deleteAll(matches);

        // Delete league table entries
        List<LeagueTableEntry> leagueEntries = leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily);
        leagueTableEntryRepository.deleteAll(leagueEntries);

        // Delete teams
        List<Team> teams = teamRepository.findByDaily(daily);
        for (Team team : teams) {
            team.getPlayers().clear();
            teamRepository.save(team);
        }
        teamRepository.deleteAll(teams);

        // Clear confirmed players
        daily.getConfirmedPlayers().clear();
        dailyRepository.save(daily);

        dailyRepository.delete(daily);
    }

    @Transactional
    public DailyListItemDTO uploadChampionImage(Long id, MultipartFile file, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can upload champion image");
        }

        if (!"FINISHED".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Champion image can only be uploaded for FINISHED dailies");
        }

        String filename = fileUploadService.uploadImage(file);
        daily.setChampionImage(filename);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional(readOnly = true)
    public DailyDetailDTO getDailyDetail(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        List<PlayerDTO> confirmedPlayers = daily.getConfirmedPlayers().stream()
                .map(u -> PlayerDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .image(u.getImage())
                        .stars(u.getStars())
                        .position(u.getPosition())
                        .build())
                .collect(Collectors.toList());

        List<Team> teams = teamRepository.findByDaily(daily);
        List<TeamDTO> teamDTOs = teams.stream()
                .map(dailyDTOMapper::buildTeamDTO)
                .collect(Collectors.toList());

        List<Match> matches = matchRepository.findByDaily(daily);
        List<PlayerMatchStat> allDetailStats = playerMatchStatRepository.findByMatchInWithUser(matches);
        Map<Long, List<PlayerMatchStat>> detailStatsByMatchId = allDetailStats.stream()
                .collect(Collectors.groupingBy(s -> s.getMatch().getId()));

        List<MatchDTO> matchDTOs = matches.stream()
                .map(m -> {
                    List<PlayerStatDTO> playerStatDTOs = detailStatsByMatchId
                            .getOrDefault(m.getId(), Collections.emptyList()).stream()
                            .filter(s -> s.getGoals() > 0 || s.getAssists() > 0)
                            .map(s -> PlayerStatDTO.builder()
                                    .userId(s.getUser().getId())
                                    .goals(s.getGoals())
                                    .assists(s.getAssists())
                                    .build())
                            .collect(Collectors.toList());
                    return MatchDTO.builder()
                            .id(m.getId())
                            .team1Id(m.getTeam1().getId())
                            .team1Name(m.getTeam1().getName())
                            .team2Id(m.getTeam2().getId())
                            .team2Name(m.getTeam2().getName())
                            .team1Score(m.getTeam1Score())
                            .team2Score(m.getTeam2Score())
                            .winnerId(m.getWinner() != null ? m.getWinner().getId() : null)
                            .playerStats(playerStatDTOs)
                            .build();
                })
                .collect(Collectors.toList());

        List<UserDailyStats> statsEntities = userDailyStatsRepository.findByDaily(daily);
        List<UserDailyStatsDTO> playerStats = statsEntities.stream()
                .map(s -> UserDailyStatsDTO.builder()
                        .userId(s.getUser().getId())
                        .username(s.getUser().getUsername())
                        .goals(s.getGoals())
                        .assists(s.getAssists())
                        .matchesPlayed(s.getMatchesPlayed())
                        .wins(s.getWins())
                        .build())
                .collect(Collectors.toList());

        List<LeagueTableEntry> leagueEntities = leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily);
        List<LeagueTableEntryDTO> leagueTable = leagueEntities.stream()
                .map(e -> LeagueTableEntryDTO.builder()
                        .teamId(e.getTeam().getId())
                        .teamName(e.getTeam().getName())
                        .position(e.getPosition())
                        .wins(e.getWins())
                        .draws(e.getDraws())
                        .losses(e.getLosses())
                        .goalsFor(e.getGoalsFor())
                        .goalsAgainst(e.getGoalsAgainst())
                        .goalDiff(e.getGoalsFor() - e.getGoalsAgainst())
                        .points(e.getPoints())
                        .build())
                .collect(Collectors.toList());

        AwardDTO award = dailyAwardRepository.findByDaily(daily)
                .map(a -> AwardDTO.builder()
                        .puskasWinnerIds(a.getPuskasWinners().stream().map(User::getId).collect(Collectors.toList()))
                        .puskasWinnerNames(a.getPuskasWinners().stream().map(User::getUsername).collect(Collectors.toList()))
                        .wiltballWinnerIds(a.getWiltballWinners().stream().map(User::getId).collect(Collectors.toList()))
                        .wiltballWinnerNames(a.getWiltballWinners().stream().map(User::getUsername).collect(Collectors.toList()))
                        .artilheiroWinnerIds(a.getArtilheiroWinners().stream().map(User::getId).collect(Collectors.toList()))
                        .artilheiroWinnerNames(a.getArtilheiroWinners().stream().map(User::getUsername).collect(Collectors.toList()))
                        .garcomWinnerIds(a.getGarcomWinners().stream().map(User::getId).collect(Collectors.toList()))
                        .garcomWinnerNames(a.getGarcomWinners().stream().map(User::getUsername).collect(Collectors.toList()))
                        .build())
                .orElse(null);

        boolean isAdmin = pelada.getAdmins().contains(caller);

        List<PlayerDTO> peladaMembers = isAdmin
                ? pelada.getMembers().stream()
                    .map(u -> PlayerDTO.builder()
                        .id(u.getId()).username(u.getUsername()).image(u.getImage())
                        .stars(u.getStars()).position(u.getPosition()).build())
                    .collect(Collectors.toList())
                : null;

        return DailyDetailDTO.builder()
                .id(daily.getId())
                .dailyDate(daily.getDailyDate())
                .dailyTime(daily.getDailyTime())
                .status(daily.getStatus())
                .isFinished(daily.isFinished())
                .championImage(daily.getChampionImage())
                .confirmedPlayers(confirmedPlayers)
                .teams(teamDTOs)
                .matches(matchDTOs)
                .playerStats(playerStats)
                .leagueTable(leagueTable)
                .award(award)
                .peladaId(pelada.getId())
                .peladaName(pelada.getName())
                .numberOfTeams(pelada.getNumberOfTeams())
                .playersPerTeam(pelada.getPlayersPerTeam())
                .isAdmin(isAdmin)
                .peladaMembers(peladaMembers)
                .build();
    }

    @Transactional
    public DailyListItemDTO adminConfirmAttendance(Long dailyId, Long targetUserId, String callerEmail) {
        return dailyAttendanceService.adminConfirmAttendance(dailyId, targetUserId, callerEmail);
    }

    @Transactional
    public DailyDetailDTO.TeamDTO updateTeamName(Long dailyId, Long teamId, String name, String callerEmail) {
        return dailyTeamManagementService.updateTeamName(dailyId, teamId, name, callerEmail);
    }

    @Transactional
    public DailyDetailDTO.TeamDTO updateTeamColor(Long dailyId, Long teamId, String color, String callerEmail) {
        return dailyTeamManagementService.updateTeamColor(dailyId, teamId, color, callerEmail);
    }

    @Transactional
    public DailyListItemDTO adminDisconfirmAttendance(Long dailyId, Long targetUserId, String callerEmail) {
        return dailyAttendanceService.adminDisconfirmAttendance(dailyId, targetUserId, callerEmail);
    }

    @Transactional
    public DailyDetailDTO populateFromMessage(Long id, PopulateDailyRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can populate a daily from a message");
        }

        if (!"SCHEDULED".equals(daily.getStatus()) && !"CONFIRMED".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Daily must be SCHEDULED or CONFIRMED to import from message");
        }

        if (request.getTeams() == null || request.getTeams().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one team is required");
        }

        // Collect all userIds and validate they are pelada members
        List<Long> allUserIds = request.getTeams().stream()
                .flatMap(t -> t.getPlayers().stream())
                .map(PopulateDailyRequestDTO.ParsedPlayerDTO::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        java.util.Set<Long> memberIds = pelada.getMembers().stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        for (Long uid : allUserIds) {
            if (!memberIds.contains(uid)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "User " + uid + " is not a member of this pelada");
            }
        }

        // Wipe existing teams (clear join table first)
        List<Team> existingTeams = teamRepository.findByDaily(daily);
        for (Team t : existingTeams) {
            t.getPlayers().clear();
            teamRepository.save(t);
        }
        teamRepository.deleteAll(existingTeams);

        // Wipe existing matches (delete player stats first)
        List<Match> existingMatches = matchRepository.findByDaily(daily);
        for (Match m : existingMatches) {
            playerMatchStatRepository.deleteByMatch(m);
        }
        matchRepository.deleteAll(existingMatches);

        // Clear confirmed players
        daily.getConfirmedPlayers().clear();
        dailyRepository.save(daily);

        // Create teams
        Map<String, Team> colorToTeam = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.getTeams()) {
            Team team = Team.builder()
                    .daily(daily)
                    .name(parsedTeam.getColorName())
                    .color(parsedTeam.getColorHex())
                    .build();
            for (PopulateDailyRequestDTO.ParsedPlayerDTO parsedPlayer : parsedTeam.getPlayers()) {
                User u = userMap.get(parsedPlayer.getUserId());
                if (u != null) {
                    team.getPlayers().add(u);
                }
            }
            Team savedTeam = teamRepository.save(team);
            colorToTeam.put(parsedTeam.getColorName().toLowerCase(), savedTeam);
        }

        // Add all players to confirmedPlayers
        for (Long uid : allUserIds) {
            User u = userMap.get(uid);
            if (u != null) {
                daily.getConfirmedPlayers().add(u);
            }
        }
        dailyRepository.save(daily);

        // Create matches
        List<Match> savedMatches = new ArrayList<>();
        if (request.getMatches() != null) {
            for (PopulateDailyRequestDTO.ParsedMatchDTO parsedMatch : request.getMatches()) {
                Team t1 = colorToTeam.get(parsedMatch.getTeam1ColorName().toLowerCase());
                Team t2 = colorToTeam.get(parsedMatch.getTeam2ColorName().toLowerCase());
                if (t1 == null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Unknown team color: " + parsedMatch.getTeam1ColorName());
                }
                if (t2 == null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Unknown team color: " + parsedMatch.getTeam2ColorName());
                }
                Team winner = null;
                if (parsedMatch.getTeam1Score() > parsedMatch.getTeam2Score()) {
                    winner = t1;
                } else if (parsedMatch.getTeam2Score() > parsedMatch.getTeam1Score()) {
                    winner = t2;
                }
                Match match = matchRepository.save(Match.builder()
                        .daily(daily)
                        .team1(t1)
                        .team2(t2)
                        .team1Score(parsedMatch.getTeam1Score())
                        .team2Score(parsedMatch.getTeam2Score())
                        .winner(winner)
                        .build());
                savedMatches.add(match);
            }
        }

        // Distribute stats per team
        Map<Long, Map<Long, Map<Long, Integer>>> teamGoalsDist = new HashMap<>();
        Map<Long, Map<Long, Map<Long, Integer>>> teamAssistsDist = new HashMap<>();

        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.getTeams()) {
            Team team = colorToTeam.get(parsedTeam.getColorName().toLowerCase());
            List<Match> teamMatches = savedMatches.stream()
                    .filter(m -> m.getTeam1().getId().equals(team.getId()) || m.getTeam2().getId().equals(team.getId()))
                    .collect(Collectors.toList());
            teamGoalsDist.put(team.getId(), distributeStats(parsedTeam.getPlayers(), teamMatches, team, true));
            teamAssistsDist.put(team.getId(), distributeStats(parsedTeam.getPlayers(), teamMatches, team, false));
        }

        // Build player -> team map
        Map<Long, Team> playerTeamMap = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.getTeams()) {
            Team team = colorToTeam.get(parsedTeam.getColorName().toLowerCase());
            for (PopulateDailyRequestDTO.ParsedPlayerDTO p : parsedTeam.getPlayers()) {
                playerTeamMap.put(p.getUserId(), team);
            }
        }

        // Save PlayerMatchStats only for players on the two teams playing each match
        List<PlayerMatchStat> allStats = new ArrayList<>();
        for (Match match : savedMatches) {
            java.util.Set<User> matchPlayers = new java.util.LinkedHashSet<>();
            matchPlayers.addAll(match.getTeam1().getPlayers());
            matchPlayers.addAll(match.getTeam2().getPlayers());
            for (User player : matchPlayers) {
                Team playerTeam = playerTeamMap.get(player.getId());
                int goals = 0, assists = 0;
                if (playerTeam != null) {
                    Map<Long, Map<Long, Integer>> goalsForTeam = teamGoalsDist.get(playerTeam.getId());
                    Map<Long, Map<Long, Integer>> assistsForTeam = teamAssistsDist.get(playerTeam.getId());
                    if (goalsForTeam != null) {
                        goals = goalsForTeam.getOrDefault(match.getId(), new HashMap<>()).getOrDefault(player.getId(), 0);
                    }
                    if (assistsForTeam != null) {
                        assists = assistsForTeam.getOrDefault(match.getId(), new HashMap<>()).getOrDefault(player.getId(), 0);
                    }
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

        // Set status to IN_COURSE
        daily.setStatus("IN_COURSE");
        dailyRepository.save(daily);

        return getDailyDetail(id, currentUserEmail);
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
            java.util.Collections.shuffle(pool);

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
}
