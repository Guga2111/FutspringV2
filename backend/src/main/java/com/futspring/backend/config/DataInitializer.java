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