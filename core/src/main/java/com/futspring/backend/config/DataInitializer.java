package com.futspring.backend.config;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyAward;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;
    private final DailyAwardRepository dailyAwardRepository;
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
            // VERMELHO — 5G+2A, 6G+2A, 0G+1A, 2G+5A, 3G+1A
            buildUser("leal@futspring.com",    "Leal",    encodedPassword, "Atacante",  5),
            buildUser("souto@futspring.com",   "Souto",   encodedPassword, "Atacante",  5),
            buildUser("ferraz@futspring.com",  "Ferraz",  encodedPassword, "Defensor",  1),
            buildUser("lui@futspring.com",     "Lui",     encodedPassword, "Meia",      5),
            buildUser("tuca@futspring.com",    "Tuca",    encodedPassword, "Atacante",  4),
            // BRANCO — 6G+1A, 2G+4A, 2G+1A, 1G+1A, 3G+2A
            buildUser("gone@futspring.com",    "Gone",    encodedPassword, "Atacante",  5),
            buildUser("thiago@futspring.com",  "Thiago",  encodedPassword, "Meia",      4),
            buildUser("tao@futspring.com",     "Tão",     encodedPassword, "Meia",      3),
            buildUser("lobo@futspring.com",    "Lobo",    encodedPassword, "Meia",      2),
            buildUser("dudu@futspring.com",    "Dudu",    encodedPassword, "Atacante",  4),
            // PRETO — 1G+1A, 1G, 2G+1A, 1G+1A, 0
            buildUser("miguel@futspring.com",  "Miguel",  encodedPassword, "Meia",      2),
            buildUser("neto@futspring.com",    "Neto",    encodedPassword, "Atacante",  2),
            buildUser("vuzzi@futspring.com",   "Vuzzi",   encodedPassword, "Atacante",  3),
            buildUser("27@futspring.com",      "27",      encodedPassword, "Meia",      2),
            buildUser("nando@futspring.com",   "Nando",   encodedPassword, "Goleiro",   1),
            // AZUL — 1G, 1G+1A, 2G+1A, 1G+2A, 3G+1A
            buildUser("abreu@futspring.com",   "Abreu",   encodedPassword, "Atacante",  1),
            buildUser("leudo@futspring.com",   "Leudo",   encodedPassword, "Meia",      2),
            buildUser("diego@futspring.com",   "Diego",   encodedPassword, "Atacante",  3),
            buildUser("pirro@futspring.com",   "Pirro",   encodedPassword, "Meia",      3),
            buildUser("andre@futspring.com",   "André",   encodedPassword, "Atacante",  4)
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

        // Finished dailies with awards (spread over past weeks for realistic history)
        // Players by index: 0=Leal,1=Souto,2=Ferraz,3=Lui,4=Tuca,5=Gone,6=Thiago,7=Tão,
        //                   8=Lobo,9=Dudu,10=Miguel,11=Neto,12=Vuzzi,13=27,14=Nando,
        //                   15=Abreu,16=Leudo,17=Diego,18=Pirro,19=André
        record AwardData(List<User> artilheiros, List<User> garcons, List<User> puskas, List<User> wiltball) {}

        record DailySpec(LocalDate date, AwardData awards) {}

        List<DailySpec> specs = List.of(
            new DailySpec(LocalDate.now().minusWeeks(7), new AwardData(
                List.of(u(savedUsers,0), u(savedUsers,5)),
                List.of(u(savedUsers,3), u(savedUsers,6)),
                List.of(u(savedUsers,1)),
                List.of(u(savedUsers,14))
            )),
            new DailySpec(LocalDate.now().minusWeeks(6), new AwardData(
                List.of(u(savedUsers,5), u(savedUsers,17)),
                List.of(u(savedUsers,9), u(savedUsers,3)),
                List.of(u(savedUsers,0)),
                List.of(u(savedUsers,13))
            )),
            new DailySpec(LocalDate.now().minusWeeks(5), new AwardData(
                List.of(u(savedUsers,1), u(savedUsers,4)),
                List.of(u(savedUsers,7), u(savedUsers,16)),
                List.of(u(savedUsers,17)),
                List.of(u(savedUsers,2))
            )),
            new DailySpec(LocalDate.now().minusWeeks(4), new AwardData(
                List.of(u(savedUsers,0), u(savedUsers,12)),
                List.of(u(savedUsers,3), u(savedUsers,9)),
                List.of(u(savedUsers,5)),
                List.of(u(savedUsers,11))
            )),
            new DailySpec(LocalDate.now().minusWeeks(3), new AwardData(
                List.of(u(savedUsers,5), u(savedUsers,19)),
                List.of(u(savedUsers,6), u(savedUsers,4)),
                List.of(u(savedUsers,12)),
                List.of(u(savedUsers,8))
            )),
            new DailySpec(LocalDate.now().minusWeeks(2), new AwardData(
                List.of(u(savedUsers,17), u(savedUsers,0)),
                List.of(u(savedUsers,3), u(savedUsers,18)),
                List.of(u(savedUsers,1)),
                List.of(u(savedUsers,13))
            )),
            new DailySpec(LocalDate.now().minusWeeks(1), new AwardData(
                List.of(u(savedUsers,1), u(savedUsers,5)),
                List.of(u(savedUsers,9), u(savedUsers,6)),
                List.of(u(savedUsers,19)),
                List.of(u(savedUsers,14))
            ))
        );

        for (DailySpec spec : specs) {
            Daily daily = Daily.builder()
                    .pelada(pelada)
                    .dailyDate(spec.date())
                    .dailyTime("08:00")
                    .status("FINISHED")
                    .isFinished(true)
                    .confirmedPlayers(new HashSet<>(savedUsers))
                    .build();
            daily = dailyRepository.save(daily);

            DailyAward award = DailyAward.builder()
                    .daily(daily)
                    .artilheiroWinners(new java.util.ArrayList<>(spec.awards().artilheiros()))
                    .garcomWinners(new java.util.ArrayList<>(spec.awards().garcons()))
                    .puskasWinners(new java.util.ArrayList<>(spec.awards().puskas()))
                    .wiltballWinners(new java.util.ArrayList<>(spec.awards().wiltball()))
                    .build();
            dailyAwardRepository.save(award);
        }

        // Today's daily — still scheduled
        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(LocalDate.now())
                .dailyTime("08:00")
                .status("SCHEDULED")
                .confirmedPlayers(new HashSet<>(savedUsers))
                .build();

        dailyRepository.save(daily);

        log.info("DataInitializer: 7 dailies finalizados + 1 agendado criados com prêmios.");
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

    private static User u(List<User> users, int index) {
        return users.get(index);
    }
}