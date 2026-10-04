package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyDetailDTO.*;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyService {

    private final UserAuthenticationHelper userAuthHelper;
    private final DailyAttendanceService dailyAttendanceService;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyResultsService dailyResultsService;
    private final DailyDTOMapper dailyDTOMapper;
    private final DailyRepository dailyRepository;
    private final PeladaRepository peladaRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final LeagueTableEntryRepository leagueTableEntryRepository;
    private final DailyAwardRepository dailyAwardRepository;

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "SCHEDULED", Set.of("CONFIRMED", "CANCELED"),
            "CONFIRMED", Set.of("IN_COURSE", "CANCELED")
    );

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
    public DailyListItemDTO updateStatus(Long id, String newStatus, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can update daily status");
        }

        Set<String> allowed = VALID_TRANSITIONS.getOrDefault(daily.getStatus(), Set.of());
        if (!allowed.contains(newStatus)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid status transition from " + daily.getStatus() + " to " + newStatus);
        }

        daily.setStatus(newStatus);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
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

        dailyResultsService.clearResults(daily, pelada);
        dailyTeamManagementService.clearTeams(daily);
        dailyAttendanceService.clearAttendees(daily);

        dailyRepository.delete(daily);
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
}
