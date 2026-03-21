package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyDetailDTO.*;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.dto.MatchResultDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyService {

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

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
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

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
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        return dailyRepository.findByPeladaOrderByDailyDateDesc(pelada).stream()
                .map(DailyListItemDTO::from)
                .collect(Collectors.toList());
    }

    private static final java.util.Set<String> LOCKED_STATUSES = java.util.Set.of("IN_COURSE", "FINISHED", "CANCELED");

    @Transactional
    public DailyListItemDTO confirmAttendance(Long id, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot confirm attendance for a daily with status " + daily.getStatus());
        }

        if (daily.getConfirmedPlayers().contains(caller)) {
            throw new AppException(HttpStatus.CONFLICT, "You are already confirmed for this daily");
        }

        daily.getConfirmedPlayers().add(caller);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public DailyListItemDTO disconfirmAttendance(Long id, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot disconfirm attendance for a daily with status " + daily.getStatus());
        }

        if (!daily.getConfirmedPlayers().contains(caller)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "You are not confirmed for this daily");
        }

        daily.getConfirmedPlayers().remove(caller);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    private static final java.util.Map<String, java.util.Set<String>> VALID_TRANSITIONS = java.util.Map.of(
            "SCHEDULED", java.util.Set.of("CONFIRMED", "CANCELED"),
            "CONFIRMED", java.util.Set.of("IN_COURSE", "CANCELED")
    );

    @Transactional
    public DailyListItemDTO updateStatus(Long id, String newStatus, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

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
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can sort teams");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot sort teams for a daily with status " + daily.getStatus());
        }

        java.util.List<User> players = daily.getConfirmedPlayers().stream()
                .sorted(java.util.Comparator.comparingInt(User::getStars).reversed())
                .collect(Collectors.toList());

        if (players.size() < 2) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least 2 confirmed players are required to sort teams");
        }

        // Delete existing teams
        java.util.List<Team> existingTeams = teamRepository.findByDaily(daily);
        for (Team t : existingTeams) {
            t.getPlayers().clear();
            teamRepository.save(t);
        }
        teamRepository.deleteAll(existingTeams);

        // Create 2 teams (MVP default)
        Team team1 = teamRepository.save(Team.builder().daily(daily).name("Team 1").build());
        Team team2 = teamRepository.save(Team.builder().daily(daily).name("Team 2").build());
        java.util.List<Team> teams = java.util.List.of(team1, team2);

        // Greedy snake-draft: sort by stars desc, assign to team with lowest totalStars
        java.util.Map<Long, Integer> teamStars = new java.util.HashMap<>();
        teamStars.put(team1.getId(), 0);
        teamStars.put(team2.getId(), 0);

        for (User player : players) {
            Team minTeam = teams.stream()
                    .min(java.util.Comparator.comparingInt(t -> teamStars.get(t.getId())))
                    .orElseThrow();
            minTeam.getPlayers().add(player);
            teamStars.put(minTeam.getId(), teamStars.get(minTeam.getId()) + player.getStars());
        }

        teamRepository.saveAll(teams);

        return teams.stream()
                .map(team -> {
                    java.util.List<DailyDetailDTO.PlayerDTO> playerDTOs = team.getPlayers().stream()
                            .map(u -> DailyDetailDTO.PlayerDTO.builder()
                                    .id(u.getId())
                                    .username(u.getUsername())
                                    .image(u.getImage())
                                    .stars(u.getStars())
                                    .position(u.getPosition())
                                    .build())
                            .collect(Collectors.toList());
                    int totalStars = playerDTOs.stream().mapToInt(DailyDetailDTO.PlayerDTO::getStars).sum();
                    return DailyDetailDTO.TeamDTO.builder()
                            .id(team.getId())
                            .name(team.getName())
                            .totalStars(totalStars)
                            .players(playerDTOs)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public List<DailyDetailDTO.TeamDTO> swapPlayers(Long id, Long player1Id, Long player2Id, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can swap players");
        }

        java.util.List<Team> teams = teamRepository.findByDaily(daily);

        Team team1 = null;
        Team team2 = null;
        User player1 = null;
        User player2 = null;

        for (Team team : teams) {
            for (User p : team.getPlayers()) {
                if (p.getId().equals(player1Id)) {
                    team1 = team;
                    player1 = p;
                }
                if (p.getId().equals(player2Id)) {
                    team2 = team;
                    player2 = p;
                }
            }
        }

        if (team1 == null || player1 == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Player 1 is not on any team in this daily");
        }
        if (team2 == null || player2 == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Player 2 is not on any team in this daily");
        }

        team1.getPlayers().remove(player1);
        team2.getPlayers().remove(player2);
        team1.getPlayers().add(player2);
        team2.getPlayers().add(player1);

        teamRepository.save(team1);
        teamRepository.save(team2);

        return teams.stream()
                .map(team -> {
                    java.util.List<DailyDetailDTO.PlayerDTO> playerDTOs = team.getPlayers().stream()
                            .map(u -> DailyDetailDTO.PlayerDTO.builder()
                                    .id(u.getId())
                                    .username(u.getUsername())
                                    .image(u.getImage())
                                    .stars(u.getStars())
                                    .position(u.getPosition())
                                    .build())
                            .collect(Collectors.toList());
                    int totalStars = playerDTOs.stream().mapToInt(DailyDetailDTO.PlayerDTO::getStars).sum();
                    return DailyDetailDTO.TeamDTO.builder()
                            .id(team.getId())
                            .name(team.getName())
                            .totalStars(totalStars)
                            .players(playerDTOs)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public List<MatchDTO> submitResults(Long id, List<MatchResultDTO> results, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can submit results");
        }

        if (!"IN_COURSE".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Results can only be submitted for dailies with status IN_COURSE");
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
            }

            // Delete existing stats for this match
            playerMatchStatRepository.deleteAll(playerMatchStatRepository.findByMatch(savedMatch));

            // Save stats for all confirmed players
            for (User player : daily.getConfirmedPlayers()) {
                MatchResultDTO.PlayerStatInputDTO input = statsByUserId.get(player.getId());
                int goals = input != null ? input.getGoals() : 0;
                int assists = input != null ? input.getAssists() : 0;
                playerMatchStatRepository.save(PlayerMatchStat.builder()
                        .match(savedMatch)
                        .user(player)
                        .goals(goals)
                        .assists(assists)
                        .build());
            }
        }

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

    @Transactional
    public DailyDetailDTO finalizeDaily(Long id, Long puskasWinnerId, Long wiltballWinnerId, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can finalize dailies");
        }

        if (daily.isFinished()) {
            throw new AppException(HttpStatus.CONFLICT, "Daily already finalized");
        }

        if (!"IN_COURSE".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Daily must be IN_COURSE to finalize");
        }

        java.util.List<Match> matches = matchRepository.findByDaily(daily);
        if (matches.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Daily must have at least one match result to finalize");
        }

        // Validate award winners are confirmed players
        java.util.Set<User> confirmedPlayers = daily.getConfirmedPlayers();
        User puskasWinner = confirmedPlayers.stream()
                .filter(u -> u.getId().equals(puskasWinnerId))
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Puskas winner must be a confirmed player"));

        User wiltballWinner = confirmedPlayers.stream()
                .filter(u -> u.getId().equals(wiltballWinnerId))
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Wiltball winner must be a confirmed player"));

        // Compute UserDailyStats per confirmed player
        userDailyStatsRepository.deleteAll(userDailyStatsRepository.findByDaily(daily));

        java.util.Map<Long, UserDailyStats> statsMap = new java.util.HashMap<>();
        for (User player : confirmedPlayers) {
            statsMap.put(player.getId(), UserDailyStats.builder()
                    .daily(daily)
                    .user(player)
                    .build());
        }

        for (Match match : matches) {
            java.util.List<PlayerMatchStat> matchStats = playerMatchStatRepository.findByMatch(match);
            for (PlayerMatchStat stat : matchStats) {
                UserDailyStats userStats = statsMap.get(stat.getUser().getId());
                if (userStats != null) {
                    userStats.setGoals(userStats.getGoals() + stat.getGoals());
                    userStats.setAssists(userStats.getAssists() + stat.getAssists());
                    userStats.setMatchesPlayed(userStats.getMatchesPlayed() + 1);
                    if (match.getWinner() != null) {
                        // Check if this player's team is the winner
                        boolean isOnWinnerTeam = match.getWinner().getPlayers().contains(stat.getUser());
                        if (isOnWinnerTeam) {
                            userStats.setWins(userStats.getWins() + 1);
                        }
                    }
                }
            }
        }
        userDailyStatsRepository.saveAll(statsMap.values());

        // Compute LeagueTableEntry per team
        leagueTableEntryRepository.deleteAll(leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily));

        java.util.List<Team> teams = teamRepository.findByDaily(daily);
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

            if (e1 != null) {
                e1.setGoalsFor(e1.getGoalsFor() + s1);
                e1.setGoalsAgainst(e1.getGoalsAgainst() + s2);
            }
            if (e2 != null) {
                e2.setGoalsFor(e2.getGoalsFor() + s2);
                e2.setGoalsAgainst(e2.getGoalsAgainst() + s1);
            }

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

        // Sort by points desc then GD desc and set positions
        java.util.List<LeagueTableEntry> sortedEntries = leagueMap.values().stream()
                .sorted(java.util.Comparator
                        .comparingInt(LeagueTableEntry::getPoints).reversed()
                        .thenComparingInt((LeagueTableEntry e) -> e.getGoalsFor() - e.getGoalsAgainst()).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < sortedEntries.size(); i++) {
            sortedEntries.get(i).setPosition(i + 1);
        }
        leagueTableEntryRepository.saveAll(sortedEntries);

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

        // Upsert Ranking (per-pelada) and Stats (global) for each confirmed player
        java.time.LocalDate today = java.time.LocalDate.now();
        for (User player : confirmedPlayers) {
            UserDailyStats playerDailyStats = statsMap.get(player.getId());
            if (playerDailyStats == null) continue;

            boolean isWinner = winningTeam != null && winningTeam.getPlayers().contains(player);

            // Upsert Ranking
            Ranking ranking = rankingRepository.findByPeladaAndUser(pelada, player)
                    .orElse(Ranking.builder().pelada(pelada).user(player).build());
            ranking.setGoals(ranking.getGoals() + playerDailyStats.getGoals());
            ranking.setAssists(ranking.getAssists() + playerDailyStats.getAssists());
            ranking.setMatchesPlayed(ranking.getMatchesPlayed() + playerDailyStats.getMatchesPlayed());
            if (isWinner) {
                ranking.setWins(ranking.getWins() + 1);
            }
            rankingRepository.save(ranking);

            // Upsert global Stats
            Stats stats = statsRepository.findByUser(player)
                    .orElse(Stats.builder().user(player).build());
            stats.setGoals(stats.getGoals() + playerDailyStats.getGoals());
            stats.setAssists(stats.getAssists() + playerDailyStats.getAssists());
            stats.setMatchesPlayed(stats.getMatchesPlayed() + playerDailyStats.getMatchesPlayed());
            if (isWinner) {
                stats.setWins(stats.getWins() + 1);
            }
            if (player.getId().equals(puskasWinnerId)) {
                stats.getPuskasDates().add(today);
            }
            statsRepository.save(stats);
        }

        // Create or update DailyAward
        DailyAward award = dailyAwardRepository.findByDaily(daily).orElse(DailyAward.builder().daily(daily).build());
        award.setPuskasWinner(puskasWinner);
        award.setWiltballWinner(wiltballWinner);
        dailyAwardRepository.save(award);

        // Mark daily as FINISHED
        daily.setStatus("FINISHED");
        daily.setFinished(true);
        dailyRepository.save(daily);

        return getDailyDetail(id, currentUserEmail);
    }

    @Transactional
    public DailyListItemDTO uploadChampionImage(Long id, MultipartFile file, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can upload champion image");
        }

        if (!"FINISHED".equals(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Champion image can only be uploaded for FINISHED dailies");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new AppException(HttpStatus.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;

        try {
            Path uploadPath = Paths.get(uploadsDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        daily.setChampionImage(filename);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional(readOnly = true)
    public DailyDetailDTO getDailyDetail(Long id, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

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
                .map(team -> {
                    List<PlayerDTO> players = team.getPlayers().stream()
                            .map(u -> PlayerDTO.builder()
                                    .id(u.getId())
                                    .username(u.getUsername())
                                    .image(u.getImage())
                                    .stars(u.getStars())
                                    .position(u.getPosition())
                                    .build())
                            .collect(Collectors.toList());
                    int totalStars = players.stream().mapToInt(PlayerDTO::getStars).sum();
                    return TeamDTO.builder()
                            .id(team.getId())
                            .name(team.getName())
                            .totalStars(totalStars)
                            .players(players)
                            .build();
                })
                .collect(Collectors.toList());

        List<Match> matches = matchRepository.findByDaily(daily);
        List<MatchDTO> matchDTOs = matches.stream()
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
                        .puskasWinnerId(a.getPuskasWinner() != null ? a.getPuskasWinner().getId() : null)
                        .puskasWinnerName(a.getPuskasWinner() != null ? a.getPuskasWinner().getUsername() : null)
                        .wiltballWinnerId(a.getWiltballWinner() != null ? a.getWiltballWinner().getId() : null)
                        .wiltballWinnerName(a.getWiltballWinner() != null ? a.getWiltballWinner().getUsername() : null)
                        .build())
                .orElse(null);

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
                .isAdmin(pelada.getAdmins().contains(caller))
                .build();
    }
}
