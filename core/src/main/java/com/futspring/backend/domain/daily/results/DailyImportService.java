package com.futspring.backend.domain.daily.results;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.DailyTeamManagementService;
import com.futspring.backend.domain.daily.attendance.DailyAttendance;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.subscription.SubscriptionGuard;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Imports a session from a WhatsApp message: teams, attendance, matches and each player's goals and assists. */
@Service
@RequiredArgsConstructor
public class DailyImportService {

    private static final Set<DailyStatus> IMPORT_STATUSES = Set.of(DailyStatus.SCHEDULED, DailyStatus.CONFIRMED);

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyRepository dailyRepository;
    private final DailyAttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final SubscriptionGuard subscriptionGuard;

    @Transactional
    public void populateFromMessage(Long id, PopulateDailyRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        subscriptionGuard.requireWritable(pelada);

        if (!IMPORT_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Só é possível importar em sessões agendadas ou confirmadas");
        }

        // Every player once, team colors unique, every player a member of the pelada
        List<Long> allUserIds = new ArrayList<>();
        Set<Long> seenUsers = new HashSet<>();
        Set<String> seenColors = new HashSet<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO team : request.teams()) {
            if (!seenColors.add(team.colorName().toLowerCase(Locale.ROOT))) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Time repetido: " + team.colorName());
            }
            for (PopulateDailyRequestDTO.ParsedPlayerDTO player : team.players()) {
                if (!seenUsers.add(player.userId())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Um jogador aparece em mais de um time");
                }
                allUserIds.add(player.userId());
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
        attendanceRepository.deleteAllOfDaily(daily);

        Map<String, Team> colorToTeam = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.teams()) {
            Team team = Team.builder()
                    .daily(daily)
                    .name(parsedTeam.colorName())
                    .color(parsedTeam.colorHex())
                    .build();
            parsedTeam.players().forEach(p -> team.getPlayers().add(userMap.get(p.userId())));
            colorToTeam.put(parsedTeam.colorName().toLowerCase(Locale.ROOT), teamRepository.save(team));
        }

        // The imported players are the session's attendance, in the message's order
        Instant now = Instant.now();
        attendanceRepository.saveAll(allUserIds.stream()
                .map(uid -> new DailyAttendance(daily, userMap.get(uid), now))
                .toList());

        List<Match> savedMatches = new ArrayList<>();
        if (request.matches() != null) {
            for (PopulateDailyRequestDTO.ParsedMatchDTO parsedMatch : request.matches()) {
                Team t1 = colorToTeam.get(parsedMatch.team1ColorName().toLowerCase(Locale.ROOT));
                Team t2 = colorToTeam.get(parsedMatch.team2ColorName().toLowerCase(Locale.ROOT));
                if (t1 == null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Time desconhecido: " + parsedMatch.team1ColorName());
                }
                if (t2 == null) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Time desconhecido: " + parsedMatch.team2ColorName());
                }
                if (t1.getId().equals(t2.getId())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Uma partida precisa de dois times diferentes: " + parsedMatch.team1ColorName());
                }
                Team winner = parsedMatch.team1Score() > parsedMatch.team2Score() ? t1
                        : parsedMatch.team2Score() > parsedMatch.team1Score() ? t2 : null;
                savedMatches.add(matchRepository.save(Match.builder()
                        .daily(daily)
                        .team1(t1)
                        .team2(t2)
                        .team1Score(parsedMatch.team1Score())
                        .team2Score(parsedMatch.team2Score())
                        .winner(winner)
                        .build()));
            }
        }

        // Distribute each player's totals over the matches of their team
        Map<Long, Map<Long, Map<Long, Integer>>> teamGoalsDist = new HashMap<>();
        Map<Long, Map<Long, Map<Long, Integer>>> teamAssistsDist = new HashMap<>();
        Map<Long, Team> playerTeamMap = new HashMap<>();
        for (PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam : request.teams()) {
            Team team = colorToTeam.get(parsedTeam.colorName().toLowerCase(Locale.ROOT));
            List<Match> teamMatches = savedMatches.stream()
                    .filter(m -> m.getTeam1().getId().equals(team.getId()) || m.getTeam2().getId().equals(team.getId()))
                    .toList();
            teamGoalsDist.put(team.getId(), distributeStats(parsedTeam.players(), teamMatches, team, true));
            teamAssistsDist.put(team.getId(), distributeStats(parsedTeam.players(), teamMatches, team, false));
            parsedTeam.players().forEach(p -> playerTeamMap.put(p.userId(), team));
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
            int count = isGoals ? p.totalGoals() : p.totalAssists();
            if (count > 0) {
                remaining.put(p.userId(), count);
            }
        }

        // result[matchId][userId] = count assigned
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (Match m : teamMatches) {
            Map<Long, Integer> matchMap = new HashMap<>();
            for (PopulateDailyRequestDTO.ParsedPlayerDTO p : players) {
                matchMap.put(p.userId(), 0);
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

    private Daily findDaily(Long id) {
        return dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }
}
