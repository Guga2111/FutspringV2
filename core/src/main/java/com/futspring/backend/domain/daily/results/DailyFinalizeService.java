package com.futspring.backend.domain.daily.results;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyListItemAssembler;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.model.DailyAward;
import com.futspring.backend.domain.stats.model.DailyAwardRepository;
import com.futspring.backend.domain.stats.model.UserDailyStats;
import com.futspring.backend.domain.stats.model.UserDailyStatsRepository;
import com.futspring.backend.domain.subscription.SubscriptionGuard;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closing a session: its UserDailyStats, final league table, winning team and awards, the Ranking/Stats rebuild,
 * and the champion photo of a finished session.
 */
@Service
@RequiredArgsConstructor
public class DailyFinalizeService {

    private static final Set<DailyStatus> RESULT_STATUSES = Set.of(DailyStatus.IN_COURSE, DailyStatus.FINISHED);

    private final FileUploadService fileUploadService;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final AggregateRebuildService aggregateRebuildService;
    private final DailyLeagueTable leagueTable;
    private final DailyRepository dailyRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final DailyListItemAssembler dailyListItemAssembler;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionGuard subscriptionGuard;

    @Transactional
    public void finalizeDaily(Long id, List<Long> puskasWinnerIds, List<Long> wiltballWinnerIds, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        subscriptionGuard.requireWritable(pelada);

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

        // Finalizing again (to change the voted awards) doesn't announce the awards a second time
        boolean firstFinalize = daily.getStatus() != DailyStatus.FINISHED;
        DailyAward award = closeSession(daily, teamsWithPlayers, matches, puskasWinners, wiltballWinners);
        eventPublisher.publishEvent(new DailyFinalizedEvent(daily, caller, sessionPlayers.values(), firstFinalize,
                award.getPuskasWinners(), award.getWiltballWinners(),
                award.getArtilheiroWinners(), award.getGarcomWinners()));
    }

    /**
     * The results of a finalized session were edited: recomputes its stats, awards, Ranking and Stats in the
     * caller's transaction, keeping the voted awards (Puskás, Bola Murcha) of players still on its teams.
     */
    public void recomputeFinishedSession(Daily daily, List<Team> teamsWithPlayers, List<Match> matches) {
        Map<Long, User> sessionPlayers = sessionPlayers(teamsWithPlayers);
        DailyAward award = dailyAwardRepository.findByDaily(daily).orElse(null);
        closeSession(daily, teamsWithPlayers, matches,
                keepSessionPlayers(award != null ? award.getPuskasWinners() : null, sessionPlayers),
                keepSessionPlayers(award != null ? award.getWiltballWinners() : null, sessionPlayers));
    }

    @Transactional
    public DailyListItemDTO uploadChampionImage(Long id, MultipartFile file, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireAdmin(daily.getPelada(), caller);
        subscriptionGuard.requireWritable(daily.getPelada());

        if (daily.getStatus() != DailyStatus.FINISHED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A foto dos campeões só pode ser enviada em sessões finalizadas");
        }

        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(daily.getChampionImage());
        daily.setChampionImage(filename);
        dailyRepository.save(daily);
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    /**
     * Computes the session's UserDailyStats, league table and awards from its matches, rebuilds Ranking/Stats and
     * marks the daily FINISHED. The session's players are the players on its teams (the ones with match stats),
     * not the confirmed list, which can change after the teams are sorted. Returns the saved awards.
     */
    private DailyAward closeSession(Daily daily, List<Team> teamsWithPlayers, List<Match> matches,
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

        List<LeagueTableEntry> sortedEntries = leagueTable.replace(daily, teamsWithPlayers, matches);

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
        return award;
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

    private Daily findDaily(Long id) {
        return dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }
}
