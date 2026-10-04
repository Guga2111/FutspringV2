package com.futspring.backend.dev;

import com.futspring.backend.domain.daily.DailyResultsService;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.entity.Team;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
// Only with SPRING_PROFILES_ACTIVE=dev (docker-compose.dev.yml): seeds users with a known password
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String[] TEAM_NAMES = {"Vermelho", "Branco", "Preto", "Azul"};
    private static final String[] TEAM_COLORS = {"#ef4444", "#f8fafc", "#1e293b", "#3b82f6"};

    private final UserRepository userRepository;
    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;
    private final TeamRepository teamRepository;
    private final DailyResultsService dailyResultsService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("DataInitializer: dados já existem, pulando seed.");
            return;
        }

        log.info("DataInitializer: criando 20 usuários e pelada de seed...");

        String encodedPassword = passwordEncoder.encode("senha123");

        List<User> users = List.of(
            buildUser("leal@futspring.com",    "Leal",    encodedPassword, "ATACANTE",  5),
            buildUser("souto@futspring.com",   "Souto",   encodedPassword, "ATACANTE",  5),
            buildUser("ferraz@futspring.com",  "Ferraz",  encodedPassword, "ZAGUEIRO",  1),
            buildUser("lui@futspring.com",     "Lui",     encodedPassword, "MEIO",      5),
            buildUser("tuca@futspring.com",    "Tuca",    encodedPassword, "ATACANTE",  4),
            buildUser("gone@futspring.com",    "Gone",    encodedPassword, "ATACANTE",  5),
            buildUser("thiago@futspring.com",  "Thiago",  encodedPassword, "MEIO",      4),
            buildUser("tao@futspring.com",     "Tão",     encodedPassword, "MEIO",      3),
            buildUser("lobo@futspring.com",    "Lobo",    encodedPassword, "MEIO",      2),
            buildUser("dudu@futspring.com",    "Dudu",    encodedPassword, "ATACANTE",  4),
            buildUser("miguel@futspring.com",  "Miguel",  encodedPassword, "MEIO",      2),
            buildUser("neto@futspring.com",    "Neto",    encodedPassword, "ATACANTE",  2),
            buildUser("vuzzi@futspring.com",   "Vuzzi",   encodedPassword, "ATACANTE",  3),
            buildUser("27@futspring.com",      "27",      encodedPassword, "MEIO",      2),
            buildUser("nando@futspring.com",   "Nando",   encodedPassword, "GOLEIRO",   1),
            buildUser("abreu@futspring.com",   "Abreu",   encodedPassword, "ATACANTE",  1),
            buildUser("leudo@futspring.com",   "Leudo",   encodedPassword, "MEIO",      2),
            buildUser("diego@futspring.com",   "Diego",   encodedPassword, "ATACANTE",  3),
            buildUser("pirro@futspring.com",   "Pirro",   encodedPassword, "MEIO",      3),
            buildUser("andre@futspring.com",   "André",   encodedPassword, "ATACANTE",  4)
        );

        List<User> savedUsers = userRepository.saveAll(users);

        User admin = savedUsers.get(0);

        Pelada pelada = Pelada.builder()
                .name("Pelada do Fut")
                .dayOfWeek("SATURDAY")
                .timeOfDay("08:00")
                .duration(2.0f)
                .address("Rua das Chuteiras, 123")
                .reference("Campo Society da esquina")
                .playersPerTeam(5)
                .numberOfTeams(4)
                .autoCreateDailyEnabled(false)
                .creator(admin)
                .build();

        pelada.getMembers().addAll(savedUsers);
        pelada.getAdmins().add(admin);

        peladaRepository.save(pelada);

        log.info("DataInitializer: pelada '{}' criada com {} membros.", pelada.getName(), savedUsers.size());

        // Finished dailies, one per week. Sessions with 3 teams confirm only 15 players,
        // so players' histories differ. Fixed seed → same data on every run.
        Random random = new Random(2026);
        int[] teamsPerSession = {4, 3, 4, 4, 3, 4, 4};
        for (int i = 0; i < teamsPerSession.length; i++) {
            LocalDate date = LocalDate.now().minusWeeks(teamsPerSession.length - i);
            seedFinishedDaily(pelada, admin, savedUsers, date, teamsPerSession[i], random);
        }

        // Today's daily — still scheduled
        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(LocalDate.now())
                .dailyTime("08:00")
                .status(DailyStatus.SCHEDULED)
                .confirmedPlayers(new HashSet<>(savedUsers))
                .build();

        dailyRepository.save(daily);

        log.info("DataInitializer: {} dailies finalizados (times, partidas, estatísticas, prêmios) + 1 agendado.",
                teamsPerSession.length);
    }

    // Creates teams and random results, then runs the real submit + finalize flow so match stats,
    // league table, UserDailyStats, Ranking, Stats and awards are consistent with the app's rules.
    private void seedFinishedDaily(Pelada pelada, User admin, List<User> allUsers, LocalDate date,
                                   int teamCount, Random random) {
        List<User> shuffled = new ArrayList<>(allUsers);
        Collections.shuffle(shuffled, random);
        List<User> confirmed = shuffled.subList(0, teamCount * pelada.getPlayersPerTeam());

        Daily daily = dailyRepository.save(Daily.builder()
                .pelada(pelada)
                .dailyDate(date)
                .dailyTime(pelada.getTimeOfDay())
                .status(DailyStatus.IN_COURSE)
                .confirmedPlayers(new HashSet<>(confirmed))
                .build());

        List<Team> teams = new ArrayList<>();
        Map<Long, List<User>> playersByTeamId = new HashMap<>();
        for (int t = 0; t < teamCount; t++) {
            List<User> players = confirmed.subList(t * pelada.getPlayersPerTeam(), (t + 1) * pelada.getPlayersPerTeam());
            Team team = teamRepository.save(Team.builder()
                    .daily(daily)
                    .name(TEAM_NAMES[t])
                    .color(TEAM_COLORS[t])
                    .players(new HashSet<>(players))
                    .build());
            teams.add(team);
            playersByTeamId.put(team.getId(), players);
        }

        // Round robin; with 3 teams play it twice so every session has 6 matches
        int rounds = teamCount == 3 ? 2 : 1;
        List<MatchResultDTO> results = new ArrayList<>();
        List<User> scorers = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            for (int a = 0; a < teams.size(); a++) {
                for (int b = a + 1; b < teams.size(); b++) {
                    Team t1 = teams.get(a);
                    Team t2 = teams.get(b);
                    int score1 = random.nextInt(5);
                    int score2 = random.nextInt(5);
                    Map<Long, int[]> stats = new HashMap<>();
                    scorers.addAll(distributeGoals(playersByTeamId.get(t1.getId()), score1, stats, random));
                    scorers.addAll(distributeGoals(playersByTeamId.get(t2.getId()), score2, stats, random));

                    List<MatchResultDTO.PlayerStatInputDTO> playerStats = stats.entrySet().stream()
                            .map(e -> new MatchResultDTO.PlayerStatInputDTO(e.getKey(), e.getValue()[0], e.getValue()[1]))
                            .toList();
                    results.add(new MatchResultDTO(null, t1.getId(), t2.getId(), score1, score2, playerStats));
                }
            }
        }

        dailyResultsService.submitResults(daily.getId(), results, admin.getEmail());

        List<Long> puskas = scorers.isEmpty()
                ? List.of()
                : List.of(scorers.get(random.nextInt(scorers.size())).getId());
        List<Long> wiltball = List.of(confirmed.get(random.nextInt(confirmed.size())).getId());
        dailyResultsService.finalizeDaily(daily.getId(), puskas, wiltball, admin.getEmail());
    }

    // Each goal goes to a player weighted by stars; 60% of goals get an assist from a teammate.
    // stats maps userId → {goals, assists}. Returns one entry per goal scored (for the Puskás pick).
    private List<User> distributeGoals(List<User> players, int goals, Map<Long, int[]> stats, Random random) {
        List<User> scorers = new ArrayList<>();
        for (int g = 0; g < goals; g++) {
            User scorer = pickWeightedByStars(players, random);
            stats.computeIfAbsent(scorer.getId(), id -> new int[2])[0]++;
            scorers.add(scorer);

            if (random.nextDouble() < 0.6) {
                List<User> teammates = players.stream().filter(p -> !p.getId().equals(scorer.getId())).toList();
                User assister = teammates.get(random.nextInt(teammates.size()));
                stats.computeIfAbsent(assister.getId(), id -> new int[2])[1]++;
            }
        }
        return scorers;
    }

    private User pickWeightedByStars(List<User> players, Random random) {
        int total = players.stream().mapToInt(p -> p.getStars() + 1).sum();
        int roll = random.nextInt(total);
        for (User player : players) {
            roll -= player.getStars() + 1;
            if (roll < 0) return player;
        }
        return players.get(players.size() - 1);
    }

    private User buildUser(String email, String username, String password, String position, int stars) {
        return User.builder()
                .email(email)
                .username(username)
                .password(password)
                .position(position)
                .stars(stars)
                .build();
    }
}
