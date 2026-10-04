package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.Team;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyTeamManagementService {

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyRepository dailyRepository;
    private final TeamRepository teamRepository;
    private final DailyDTOMapper dailyDTOMapper;

    @Transactional
    public List<DailyDetailDTO.TeamDTO> sortTeams(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        requireUnlocked(daily);

        // Group by star rating, shuffle within each tier, then flatten (desc by stars)
        List<User> players = daily.getConfirmedPlayers().stream()
                .collect(Collectors.groupingBy(User::getStars))
                .entrySet().stream()
                .sorted(Map.Entry.<Integer, List<User>>comparingByKey().reversed())
                .flatMap(e -> {
                    Collections.shuffle(e.getValue());
                    return e.getValue().stream();
                })
                .collect(Collectors.toList());

        int numberOfTeams = pelada.getNumberOfTeams();
        int playersPerTeam = pelada.getPlayersPerTeam();
        int required = numberOfTeams * playersPerTeam;

        if (players.size() != required) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "São necessários exatamente " + required + " jogadores confirmados (" +
                    numberOfTeams + " times × " + playersPerTeam + " jogadores). Confirmados: " + players.size());
        }

        clearTeams(daily);

        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            teams.add(teamRepository.save(Team.builder().daily(daily).name("Time " + i).build()));
        }

        // LPT (longest processing time): assign each player (highest stars first)
        // to the non-full team with the lowest current total, which minimises imbalance
        int[] totals = new int[numberOfTeams];
        int[] sizes = new int[numberOfTeams];
        for (User player : players) {
            int minIdx = -1;
            int minTotal = Integer.MAX_VALUE;
            for (int j = 0; j < numberOfTeams; j++) {
                if (sizes[j] < playersPerTeam && totals[j] < minTotal) {
                    minTotal = totals[j];
                    minIdx = j;
                }
            }
            teams.get(minIdx).getPlayers().add(player);
            totals[minIdx] += player.getStars();
            sizes[minIdx]++;
        }

        teamRepository.saveAll(teams);
        return teams.stream().map(dailyDTOMapper::buildTeamDTO).toList();
    }

    @Transactional
    public List<DailyDetailDTO.TeamDTO> swapPlayers(Long id, Long player1Id, Long player2Id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireAdmin(daily.getPelada(), caller);
        requireUnlocked(daily);

        List<Team> teams = teamRepository.findByDailyWithPlayers(daily);

        Team team1 = null;
        Team team2 = null;
        User player1 = null;
        User player2 = null;
        for (Team team : teams) {
            for (User p : team.getPlayers()) {
                if (p.getId().equals(player1Id)) {
                    team1 = team;
                    player1 = p;
                }
                if (p.getId().equals(player2Id)) {
                    team2 = team;
                    player2 = p;
                }
            }
        }

        if (player1 == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "O jogador 1 não está em nenhum time desta sessão");
        }
        if (player2 == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "O jogador 2 não está em nenhum time desta sessão");
        }

        team1.getPlayers().remove(player1);
        team2.getPlayers().remove(player2);
        team1.getPlayers().add(player2);
        team2.getPlayers().add(player1);

        teamRepository.save(team1);
        teamRepository.save(team2);
        return teams.stream().map(dailyDTOMapper::buildTeamDTO).toList();
    }

    @Transactional
    public DailyDetailDTO.TeamDTO updateTeamName(Long dailyId, Long teamId, String name, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Daily daily = findDaily(dailyId);
        accessHelper.requireMember(daily.getPelada(), caller);
        Team team = findTeamInDaily(teamId, dailyId);

        if (!team.getPlayers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas jogadores do time podem renomeá-lo");
        }

        team.setName(name.trim());
        teamRepository.save(team);
        return dailyDTOMapper.buildTeamDTO(team);
    }

    @Transactional
    public DailyDetailDTO.TeamDTO updateTeamColor(Long dailyId, Long teamId, String color, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Daily daily = findDaily(dailyId);
        Pelada pelada = daily.getPelada();
        accessHelper.requireMember(pelada, caller);
        Team team = findTeamInDaily(teamId, dailyId);

        if (!team.getPlayers().contains(caller) && !accessHelper.isAdmin(pelada, caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas jogadores do time ou administradores podem mudar a cor");
        }

        team.setColor(color);
        teamRepository.save(team);
        return dailyDTOMapper.buildTeamDTO(team);
    }

    /** Deletes every team of the daily (join rows first). Used by sort, import from message and daily delete. */
    void clearTeams(Daily daily) {
        List<Team> teams = teamRepository.findByDaily(daily);
        for (Team team : teams) {
            team.getPlayers().clear();
        }
        teamRepository.deleteAll(teams);
    }

    private Daily findDaily(Long id) {
        return dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }

    private Team findTeamInDaily(Long teamId, Long dailyId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Time não encontrado"));
        if (!team.getDaily().getId().equals(dailyId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Time não encontrado nesta sessão");
        }
        return team;
    }

    private static void requireUnlocked(Daily daily) {
        if (daily.getStatus().isLocked()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Não é possível alterar os times de uma sessão com status " + daily.getStatus());
        }
    }
}
