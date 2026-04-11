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
import java.util.Collections;
import java.util.HashMap;
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
    private final DailyResultsService dailyResultsService;
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
        return dailyResultsService.submitResults(id, results, currentUserEmail);
    }

    @Transactional
    public DailyDetailDTO finalizeDaily(Long id, List<Long> puskasWinnerIds, List<Long> wiltballWinnerIds, String currentUserEmail) {
        dailyResultsService.finalizeDaily(id, puskasWinnerIds, wiltballWinnerIds, currentUserEmail);
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
        return dailyResultsService.uploadChampionImage(id, file, currentUserEmail);
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
        dailyResultsService.populateFromMessage(id, request, currentUserEmail);
        return getDailyDetail(id, currentUserEmail);
    }
}
