package com.futspring.backend.domain.daily.results;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyDTOMapper;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO.MatchDTO;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.stats.model.DailyAwardRepository;
import com.futspring.backend.domain.stats.model.UserDailyStats;
import com.futspring.backend.domain.stats.model.UserDailyStatsRepository;
import com.futspring.backend.domain.subscription.SubscriptionGuard;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Results of a session: the admin's full set of matches with each player's goals and assists (live league table,
 * or a recomputed finalized session), and wiping them when the session is deleted. Finalizing lives in
 * {@link DailyFinalizeService}, importing from a message in {@link DailyImportService}.
 */
@Service
@RequiredArgsConstructor
public class DailyResultsService {

    private static final Set<DailyStatus> RESULT_STATUSES = Set.of(DailyStatus.IN_COURSE, DailyStatus.FINISHED);

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyFinalizeService dailyFinalizeService;
    private final DailyLeagueTable leagueTable;
    private final DailyDTOMapper dailyDTOMapper;
    private final DailyRepository dailyRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final SubscriptionGuard subscriptionGuard;

    @Transactional
    public List<MatchDTO> submitResults(Long id, List<MatchResultDTO> results, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireAdmin(daily.getPelada(), caller);
        subscriptionGuard.requireWritable(daily.getPelada());

        if (!RESULT_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Resultados só podem ser lançados em sessões em andamento ou finalizadas");
        }

        Map<Long, Team> teamMap = teamRepository.findByDailyWithPlayers(daily).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
        // The session's matches, loaded once: a match id from the body must be one of them (never another pelada's match)
        Map<Long, Match> existingMatches = matchRepository.findByDaily(daily).stream()
                .collect(Collectors.toMap(Match::getId, m -> m));

        // 1. Validate every result before writing anything
        List<PlannedMatch> planned = new ArrayList<>();
        Set<Long> submittedMatchIds = new HashSet<>();

        for (MatchResultDTO result : results) {
            if (result.matchId() != null && !submittedMatchIds.add(result.matchId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Partida repetida nos resultados");
            }
            Team t1 = teamMap.get(result.team1Id());
            Team t2 = teamMap.get(result.team2Id());
            if (t1 == null || t2 == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Os times informados não pertencem a esta sessão");
            }
            if (t1.getId().equals(t2.getId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Uma partida precisa de dois times diferentes");
            }

            Match match = result.matchId() == null ? Match.builder().daily(daily).build() : existingMatches.get(result.matchId());
            if (match == null) {
                throw new AppException(HttpStatus.NOT_FOUND, "Partida não encontrada nesta sessão");
            }

            Set<Long> team1Ids = t1.getPlayers().stream().map(User::getId).collect(Collectors.toSet());
            Set<Long> team2Ids = t2.getPlayers().stream().map(User::getId).collect(Collectors.toSet());

            // Validate player stats: players of these two teams only, sums within the team score
            Map<Long, MatchResultDTO.PlayerStatInputDTO> statsByUserId = new HashMap<>();
            if (result.playerStats() != null) {
                int team1Goals = 0, team2Goals = 0, team1Assists = 0, team2Assists = 0;
                for (MatchResultDTO.PlayerStatInputDTO stat : result.playerStats()) {
                    if (statsByUserId.put(stat.userId(), stat) != null) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "Jogador repetido nas estatísticas da partida");
                    }
                    if (team1Ids.contains(stat.userId())) {
                        team1Goals += stat.goals();
                        team1Assists += stat.assists();
                    } else if (team2Ids.contains(stat.userId())) {
                        team2Goals += stat.goals();
                        team2Assists += stat.assists();
                    } else {
                        throw new AppException(HttpStatus.BAD_REQUEST, "Há estatísticas de um jogador que não está nesta partida");
                    }
                }
                requireWithinScore(t1, "gols", team1Goals, result.team1Score());
                requireWithinScore(t2, "gols", team2Goals, result.team2Score());
                requireWithinScore(t1, "assistências", team1Assists, result.team1Score());
                requireWithinScore(t2, "assistências", team2Assists, result.team2Score());
            }
            planned.add(new PlannedMatch(match, t1, t2, result, statsByUserId));
        }

        // 2. Write. The stats of every saved match are overwritten (edited and removed ones in one DELETE): one row per
        // player of the two teams, so matchesPlayed counts the matches a player actually played
        if (!existingMatches.isEmpty()) {
            playerMatchStatRepository.deleteByMatchIn(existingMatches.values());
        }
        List<Match> savedMatches = new ArrayList<>();
        List<PlayerMatchStat> statsToSave = new ArrayList<>();
        for (PlannedMatch p : planned) {
            Match match = p.match();
            match.setTeam1(p.team1());
            match.setTeam2(p.team2());
            match.setTeam1Score(p.result().team1Score());
            match.setTeam2Score(p.result().team2Score());
            match.setWinner(p.result().team1Score() > p.result().team2Score() ? p.team1()
                    : p.result().team2Score() > p.result().team1Score() ? p.team2() : null);

            Match savedMatch = matchRepository.save(match);
            savedMatches.add(savedMatch);

            Set<User> matchPlayers = new LinkedHashSet<>(p.team1().getPlayers());
            matchPlayers.addAll(p.team2().getPlayers());
            for (User player : matchPlayers) {
                MatchResultDTO.PlayerStatInputDTO input = p.statsByUserId().get(player.getId());
                statsToSave.add(PlayerMatchStat.builder()
                        .match(savedMatch)
                        .user(player)
                        .goals(input != null ? input.goals() : 0)
                        .assists(input != null ? input.assists() : 0)
                        .build());
            }
        }
        playerMatchStatRepository.saveAll(statsToSave);

        // The submitted list is the session's full set of matches: saved matches left out were removed by the admin
        List<Match> removedMatches = existingMatches.values().stream()
                .filter(m -> !submittedMatchIds.contains(m.getId()))
                .toList();
        if (!removedMatches.isEmpty()) {
            matchRepository.deleteAll(removedMatches);
        }

        List<Team> teams = new ArrayList<>(teamMap.values());
        if (daily.getStatus() == DailyStatus.FINISHED) {
            // Editing a finalized session: recompute its stats, awards, Ranking and Stats, keeping the voted awards
            dailyFinalizeService.recomputeFinishedSession(daily, teams, savedMatches);
        } else {
            // Calculate and persist the live league table so it's visible before finalization
            leagueTable.replace(daily, teams, savedMatches);
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

    /**
     * Deletes every result of the daily: award, UserDailyStats, matches with their stats and the league table.
     * Returns the players whose Ranking/Stats must be rebuilt (empty unless the daily was finalized);
     * the caller rebuilds them after deleting the daily. Called by DailyService.deleteDailyData.
     */
    public Set<User> clearResults(Daily daily) {
        dailyAwardRepository.findByDaily(daily).ifPresent(dailyAwardRepository::delete);

        List<UserDailyStats> userDailyStats = userDailyStatsRepository.findByDaily(daily);
        Set<User> affected = daily.isFinished()
                ? userDailyStats.stream().map(UserDailyStats::getUser).collect(Collectors.toCollection(LinkedHashSet::new))
                : new LinkedHashSet<>();
        userDailyStatsRepository.deleteAll(userDailyStats);

        leagueTable.delete(daily);

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

    // A validated result waiting to be written
    private record PlannedMatch(Match match, Team team1, Team team2, MatchResultDTO result,
                                Map<Long, MatchResultDTO.PlayerStatInputDTO> statsByUserId) {}
}
