package com.futspring.backend.domain.daily.results;

import com.futspring.backend.domain.daily.dto.DailyDetailDTO.MatchDTO;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// Submitting results reads a constant number of queries whatever the number of matches (N+1 guard)
class DailyResultsQueryCountTest extends BaseIntegrationTest {

    @Autowired DailyResultsService resultsService;
    @Autowired DailyRepository dailyRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired PeladaRepository peladaRepository;
    @Autowired UserRepository userRepository;
    @Autowired QueryCounter queryCounter;

    User admin;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        admin = user();
        pelada = peladaRepository.save(Pelada.builder().name("Contagem").dayOfWeek("FRIDAY").timeOfDay("18:00").duration(2f)
                .creator(admin).members(new HashSet<>(Set.of(admin))).admins(new HashSet<>(Set.of(admin)))
                .numberOfTeams(2).playersPerTeam(2).build());
    }

    User user() {
        String name = "p" + UUID.randomUUID();
        return userRepository.save(User.builder().email(name + "@count.test").username(name).password("hash").build());
    }

    record Session(Daily daily, Team team1, Team team2) {}

    Session session() {
        Daily daily = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now()).dailyTime("18:00")
                .status(DailyStatus.IN_COURSE).build());
        List<Team> teams = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            Set<User> players = new HashSet<>(Set.of(user(), user()));
            pelada.getMembers().addAll(players);
            teams.add(teamRepository.save(Team.builder().daily(daily).name("Time " + (t + 1)).players(players).build()));
        }
        return new Session(daily, teams.get(0), teams.get(1));
    }

    // `matchIds` null creates new matches; otherwise edits those matches with a new score
    List<MatchResultDTO> results(Session s, int count, List<Long> matchIds, int team1Score) {
        Long scorer = s.team1().getPlayers().iterator().next().getId();
        return IntStream.range(0, count).mapToObj(i -> new MatchResultDTO(matchIds == null ? null : matchIds.get(i),
                s.team1().getId(), s.team2().getId(), team1Score, 0,
                List.of(new MatchResultDTO.PlayerStatInputDTO(scorer, team1Score, 0)))).toList();
    }

    List<Long> submit(Session s, List<MatchResultDTO> results) {
        return resultsService.submitResults(s.daily().getId(), results, admin.getEmail()).stream().map(MatchDTO::id).toList();
    }

    // Reads of creating `count` matches and then of editing them, live or after finalizing
    List<Long> reads(int count, boolean finished) {
        Session s = session();
        if (finished) {
            List<Long> ids = submit(s, results(s, count, null, 1));
            resultsService.finalizeDaily(s.daily().getId(), null, null, admin.getEmail());
            long edit = queryCounter.reads(() -> submit(s, results(s, count, ids, 2)));
            return List.of(edit);
        }
        List<Long> ids = new ArrayList<>();
        long create = queryCounter.reads(() -> ids.addAll(submit(s, results(s, count, null, 1))));
        long edit = queryCounter.reads(() -> submit(s, results(s, count, ids, 2)));
        return List.of(create, edit);
    }

    @Test
    void submittingResultsOnALiveSessionReadsAConstantNumberOfQueries() {
        reads(2, false); // warm-up: one-off context loads don't count
        assertThat(reads(8, false)).isEqualTo(reads(2, false));
    }

    @Test
    void editingResultsOfAFinishedSessionReadsAConstantNumberOfQueries() {
        reads(2, true);
        assertThat(reads(8, true)).isEqualTo(reads(2, true));
    }
}
