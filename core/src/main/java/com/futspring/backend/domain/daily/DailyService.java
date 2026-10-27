package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.AttendanceBumpDTO;
import com.futspring.backend.domain.daily.dto.CreateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO.*;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.entity.Match;
import com.futspring.backend.domain.daily.entity.PlayerMatchStat;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.LeagueTableEntryRepository;
import com.futspring.backend.domain.daily.repository.MatchRepository;
import com.futspring.backend.domain.daily.repository.PlayerMatchStatRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.repository.DailyAwardRepository;
import com.futspring.backend.domain.stats.repository.UserDailyStatsRepository;
import com.futspring.backend.domain.user.User;
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
    private final DailyAttendanceRepository attendanceRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final PeladaRepository peladaRepository;
    private final PeladaPlayerStarsRepository playerStarsRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final LeagueTableEntryRepository leagueTableEntryRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final DailyListItemAssembler dailyListItemAssembler;

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

        return dailyListItemAssembler.toNewListItem(dailyRepository.save(daily), caller);
    }

    @Transactional(readOnly = true)
    public List<DailyListItemDTO> getDailiesForPelada(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireMember(pelada, caller);

        return dailyListItemAssembler.toListItems(dailyRepository.findByPeladaOrderByDailyDateDesc(pelada), caller);
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
        return dailyListItemAssembler.toListItem(daily, caller);
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
     * Deletes a daily and everything that hangs from it (results, teams, attendance, bumps, champion image).
     * Returns the players whose Ranking/Stats must be rebuilt; the caller rebuilds them once.
     */
    public Set<User> deleteDailyData(Daily daily) {
        Set<User> affected = dailyResultsService.clearResults(daily);
        dailyTeamManagementService.clearTeams(daily);
        dailyAttendanceService.clearAttendees(daily);
        bumpRepository.deleteByDaily(daily);
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
        Map<Long, Integer> starsByUserId = playerStarsRepository.findStarsByPelada(pelada);

        Set<Long> mensalistaIds = mensalistaRepository.findUserIdsByPelada(pelada);

        List<PlayerDTO> confirmedPlayers = attendanceRepository.findByDailyWithUser(daily).stream()
                .sorted(Comparator.comparing(a -> a.getUser().getUsername(), String.CASE_INSENSITIVE_ORDER))
                .map(a -> dailyDTOMapper.toAttendeeDTO(a.getUser(), starsByUserId,
                        mensalistaIds.contains(a.getUser().getId()), a.getConfirmedAt()))
                .toList();

        List<AttendanceBumpDTO> bumps = new ArrayList<>();
        AttendanceBumpDTO myBump = null;
        for (DailyAttendanceBump bump : bumpRepository.findByDailyWithUsers(daily)) {
            bumps.add(AttendanceBumpDTO.from(bump));
            // newest first: the first unseen one is the caller's latest
            if (myBump == null && bump.getSeenAt() == null && bump.getUser().equals(caller)) {
                myBump = AttendanceBumpDTO.from(bump);
            }
        }

        List<TeamDTO> teamDTOs = teamRepository.findByDailyWithPlayers(daily).stream()
                .map(team -> dailyDTOMapper.buildTeamDTO(team, starsByUserId))
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

        // Every member sees the attendance list (confirmed and pending); PlayerDTO has public fields only
        List<PlayerDTO> peladaMembers = pelada.getMembers().stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(user -> dailyDTOMapper.toAttendeeDTO(user, starsByUserId, mensalistaIds.contains(user.getId()), null))
                .toList();

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
                .capacity(pelada.capacity())
                .timeZone(pelada.getTimeZone())
                .startsAt(AttendanceWindow.startsAt(daily))
                .confirmOpensAt(mensalistaIds.contains(caller.getId()) ? null : AttendanceWindow.diaristaOpensAt(daily))
                .myBump(myBump)
                .bumps(bumps)
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
