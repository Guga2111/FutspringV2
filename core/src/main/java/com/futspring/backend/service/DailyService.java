package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyDetailDTO.*;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.PeladaAccessHelper;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyService {

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyAttendanceService dailyAttendanceService;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyResultsService dailyResultsService;
    private final AggregateRebuildService aggregateRebuildService;
    private final FileUploadService fileUploadService;
    private final DailyDTOMapper dailyDTOMapper;
    private final DailyRepository dailyRepository;
    private final PeladaRepository peladaRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final LeagueTableEntryRepository leagueTableEntryRepository;
    private final DailyAwardRepository dailyAwardRepository;

    @Transactional
    public DailyListItemDTO createDaily(Long peladaId, CreateDailyRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(request.getDailyDate())
                .dailyTime(request.getDailyTime())
                .build();

        return DailyListItemDTO.from(dailyRepository.save(daily), 0);
    }

    @Transactional(readOnly = true)
    public List<DailyListItemDTO> getDailiesForPelada(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireMember(pelada, caller);

        List<Daily> dailies = dailyRepository.findByPeladaOrderByDailyDateDesc(pelada);
        if (dailies.isEmpty()) {
            return List.of();
        }
        // One count query instead of loading confirmedPlayers per daily
        Map<Long, Integer> confirmedCounts = dailyRepository
                .countConfirmedByIds(dailies.stream().map(Daily::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));

        return dailies.stream()
                .map(d -> DailyListItemDTO.from(d, confirmedCounts.getOrDefault(d.getId(), 0)))
                .toList();
    }

    @Transactional
    public DailyListItemDTO updateStatus(Long id, DailyStatus newStatus, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireAdmin(daily.getPelada(), caller);

        if (!daily.getStatus().canTransitionTo(newStatus)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Não é possível mudar o status de " + daily.getStatus() + " para " + newStatus);
        }

        daily.setStatus(newStatus);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public void deleteDaily(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);

        Set<User> affected = deleteDailyData(daily);
        aggregateRebuildService.rebuild(pelada, affected);
    }

    /**
     * Deletes a daily and everything that hangs from it (results, teams, attendance, champion image).
     * Returns the players whose Ranking/Stats must be rebuilt; the caller rebuilds them once.
     */
    Set<User> deleteDailyData(Daily daily) {
        Set<User> affected = dailyResultsService.clearResults(daily);
        dailyTeamManagementService.clearTeams(daily);
        dailyAttendanceService.clearAttendees(daily);
        fileUploadService.deleteImageAfterCommit(daily.getChampionImage());
        dailyRepository.delete(daily);
        return affected;
    }

    @Transactional(readOnly = true)
    public DailyDetailDTO getDailyDetail(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireMember(pelada, caller);
        boolean isAdmin = accessHelper.isAdmin(pelada, caller);

        List<PlayerDTO> confirmedPlayers = daily.getConfirmedPlayers().stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(dailyDTOMapper::toPlayerDTO)
                .toList();

        List<TeamDTO> teamDTOs = teamRepository.findByDailyWithPlayers(daily).stream()
                .map(dailyDTOMapper::buildTeamDTO)
                .toList();

        List<Match> matches = matchRepository.findByDailyWithTeams(daily);
        Map<Long, List<PlayerMatchStat>> statsByMatchId = matches.isEmpty()
                ? Map.of()
                : playerMatchStatRepository.findByMatchInWithUser(matches).stream()
                        .collect(Collectors.groupingBy(s -> s.getMatch().getId()));

        List<MatchDTO> matchDTOs = matches.stream()
                .map(m -> dailyDTOMapper.toMatchDTO(m, statsByMatchId.getOrDefault(m.getId(), List.of()).stream()
                        .filter(s -> s.getGoals() > 0 || s.getAssists() > 0)
                        .map(s -> PlayerStatDTO.builder()
                                .userId(s.getUser().getId())
                                .goals(s.getGoals())
                                .assists(s.getAssists())
                                .build())
                        .toList()))
                .toList();

        List<UserDailyStatsDTO> playerStats = userDailyStatsRepository.findByDailyWithUser(daily).stream()
                .map(s -> UserDailyStatsDTO.builder()
                        .userId(s.getUser().getId())
                        .username(s.getUser().getUsername())
                        .goals(s.getGoals())
                        .assists(s.getAssists())
                        .matchesPlayed(s.getMatchesPlayed())
                        .wins(s.getWins())
                        .build())
                .toList();

        List<LeagueTableEntryDTO> leagueTable = leagueTableEntryRepository.findByDailyWithTeam(daily).stream()
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
                .toList();

        AwardDTO award = dailyAwardRepository.findByDaily(daily)
                .map(a -> AwardDTO.builder()
                        .puskasWinnerIds(ids(a.getPuskasWinners()))
                        .puskasWinnerNames(names(a.getPuskasWinners()))
                        .wiltballWinnerIds(ids(a.getWiltballWinners()))
                        .wiltballWinnerNames(names(a.getWiltballWinners()))
                        .artilheiroWinnerIds(ids(a.getArtilheiroWinners()))
                        .artilheiroWinnerNames(names(a.getArtilheiroWinners()))
                        .garcomWinnerIds(ids(a.getGarcomWinners()))
                        .garcomWinnerNames(names(a.getGarcomWinners()))
                        .build())
                .orElse(null);

        // Only admins need the member list (admin attendance and import from message)
        List<PlayerDTO> peladaMembers = isAdmin
                ? pelada.getMembers().stream()
                    .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                    .map(dailyDTOMapper::toPlayerDTO)
                    .toList()
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

    private Pelada findPelada(Long id) {
        return peladaRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }

    private Daily findDaily(Long id) {
        return dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }

    private static List<Long> ids(List<User> users) {
        return map(users, User::getId);
    }

    private static List<String> names(List<User> users) {
        return map(users, User::getUsername);
    }

    private static <T> List<T> map(List<User> users, Function<User, T> f) {
        return users.stream().map(f).collect(Collectors.toList());
    }
}
