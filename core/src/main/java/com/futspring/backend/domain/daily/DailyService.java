package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.AttendanceBumpDTO;
import com.futspring.backend.domain.finance.FinancePreservation;
import com.futspring.backend.domain.daily.dto.CreateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO.*;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceBump;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.results.Match;
import com.futspring.backend.domain.daily.results.PlayerMatchStat;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.results.LeagueTableEntryRepository;
import com.futspring.backend.domain.daily.results.MatchRepository;
import com.futspring.backend.domain.daily.results.PlayerMatchStatRepository;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.daily.attendance.AttendanceWindow;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceService;
import com.futspring.backend.domain.daily.results.DailyResultsService;
import com.futspring.backend.domain.daily.team.TeamsReadiness;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.subscription.SubscriptionGuard;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.model.DailyAwardRepository;
import com.futspring.backend.domain.stats.model.UserDailyStatsRepository;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyService {

    private final FinancePreservation financePreservation;
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
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionGuard subscriptionGuard;

    @Transactional
    public DailyListItemDTO createDaily(Long peladaId, CreateDailyRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);
        subscriptionGuard.requireWritable(pelada);

        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(request.dailyDate())
                .dailyTime(request.dailyTime())
                .build();

        Daily saved = dailyRepository.save(daily);
        eventPublisher.publishEvent(new DailyCreatedEvent(saved, caller));
        return dailyListItemAssembler.toNewListItem(saved, caller);
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
        // Same row lock as attendance: a withdrawal can't race the start check
        Daily daily = dailyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
        accessHelper.requireAdmin(daily.getPelada(), caller);
        // cancelling is free in a read-only pelada: an upcoming session that can't happen can still be called off
        if (newStatus != DailyStatus.CANCELED) {
            subscriptionGuard.requireWritable(daily.getPelada());
        }

        if (!daily.getStatus().canTransitionTo(newStatus)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Não é possível mudar o status de " + daily.getStatus() + " para " + newStatus);
        }
        if (newStatus == DailyStatus.IN_COURSE) {
            Set<Long> confirmedIds = attendanceRepository.findUsersByDaily(daily).stream()
                    .map(User::getId).collect(Collectors.toSet());
            TeamsReadiness.requireStartable(teamRepository.findByDailyWithPlayers(daily), confirmedIds);
        }

        daily.setStatus(newStatus);
        dailyRepository.save(daily);
        if (newStatus == DailyStatus.CANCELED) {
            eventPublisher.publishEvent(new DailyCanceledEvent(daily, caller));
        }
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    @Transactional
    public void deleteDaily(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);

        peladaRepository.findByIdForUpdate(pelada.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        // under the pelada lock, which subscription changes take too
        subscriptionGuard.requireWritable(pelada);
        financePreservation.detachDaily(daily, caller);
        Set<User> affected = deleteDailyData(daily);
        aggregateRebuildService.rebuild(pelada, affected);
    }

    /**
     * Takes members who left the pelada out of its sessions that haven't started: attendance and sorted teams.
     * Locked sessions (live, finished, canceled) are history and stay as they are. A fixed number of queries,
     * whatever the number of members and sessions.
     */
    public void removeFromUpcomingSessions(Pelada pelada, Collection<User> users) {
        Set<DailyStatus> upcoming = EnumSet.complementOf(EnumSet.copyOf(DailyStatus.LOCKED));
        List<Daily> dailies = dailyRepository.findByPeladaAndStatusIn(pelada, upcoming);
        if (dailies.isEmpty() || users.isEmpty()) {
            return;
        }
        dailyAttendanceService.removeAttendees(dailies, users);
        dailyTeamManagementService.removePlayersFromTeams(dailies, users);
    }

    /**
     * Account deletion: the same as {@link #removeFromUpcomingSessions} for one user across every pelada, in two
     * statements whatever the number of peladas and sessions; the bumps where they lost a spot go too.
     */
    public void removeAccountFromUpcomingSessions(User user) {
        Set<DailyStatus> upcoming = EnumSet.complementOf(EnumSet.copyOf(DailyStatus.LOCKED));
        attendanceRepository.deleteByUserInDailiesWithStatus(user, upcoming);
        teamRepository.deletePlayerFromTeamsOfDailiesWithStatus(user.getId(), upcoming.stream().map(Enum::name).toList());
        bumpRepository.deleteByUser(user);
    }

    /**
     * Deletes a daily and everything that hangs from it (results, teams, attendance, bumps, notifications,
     * champion image).
     * Returns the players whose Ranking/Stats must be rebuilt; the caller rebuilds them once.
     */
    public Set<User> deleteDailyData(Daily daily) {
        Set<User> affected = dailyResultsService.clearResults(daily);
        dailyTeamManagementService.clearTeams(daily);
        dailyAttendanceService.clearAttendees(daily);
        bumpRepository.deleteByDaily(daily);
        notificationRepository.deleteByDaily(daily);
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
                .creatorId(pelada.getCreator() != null ? pelada.getCreator().getId() : null)
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
                .subscription(subscriptionGuard.summary(pelada))
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
