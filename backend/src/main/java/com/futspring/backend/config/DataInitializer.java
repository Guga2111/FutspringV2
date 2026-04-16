package com.futspring.backend.config;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
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
            buildUser("ronaldo@futspring.com",    "Ronaldo",      encodedPassword, "Atacante",  5),
            buildUser("zidane@futspring.com",     "Zidane",       encodedPassword, "Meia",      5),
            buildUser("pele@futspring.com",       "Pelé",         encodedPassword, "Atacante",  5),
            buildUser("maldini@futspring.com",    "Maldini",      encodedPassword, "Defensor",  5),
            buildUser("xavi@futspring.com",       "Xavi",         encodedPassword, "Meia",      4),
            buildUser("iniesta@futspring.com",    "Iniesta",      encodedPassword, "Meia",      4),
            buildUser("neymar@futspring.com",     "Neymar",       encodedPassword, "Atacante",  4),
            buildUser("casillas@futspring.com",   "Casillas",     encodedPassword, "Goleiro",   4),
            buildUser("cafu@futspring.com",       "Cafu",         encodedPassword, "Defensor",  3),
            buildUser("roberto@futspring.com",    "Roberto Carlos", encodedPassword, "Defensor", 3),
            buildUser("rivaldo@futspring.com",    "Rivaldo",      encodedPassword, "Atacante",  3),
            buildUser("ronaldinho@futspring.com", "Ronaldinho",   encodedPassword, "Meia",      3),
            buildUser("beckham@futspring.com",    "Beckham",      encodedPassword, "Meia",      2),
            buildUser("henry@futspring.com",      "Henry",        encodedPassword, "Atacante",  2),
            buildUser("lampard@futspring.com",    "Lampard",      encodedPassword, "Meia",      2),
            buildUser("gerrard@futspring.com",    "Gerrard",      encodedPassword, "Meia",      2),
            buildUser("drogba@futspring.com",     "Drogba",       encodedPassword, "Atacante",  1),
            buildUser("sneijder@futspring.com",   "Sneijder",     encodedPassword, "Meia",      1),
            buildUser("etoo@futspring.com",       "Eto'o",        encodedPassword, "Atacante",  1),
            buildUser("buffon@futspring.com",     "Buffon",       encodedPassword, "Goleiro",   1)
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

        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(LocalDate.now())
                .dailyTime("08:00")
                .status("SCHEDULED")
                .confirmedPlayers(new HashSet<>(savedUsers))
                .build();

        dailyRepository.save(daily);

        log.info("DataInitializer: daily criado para hoje com {} jogadores confirmados.", savedUsers.size());
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