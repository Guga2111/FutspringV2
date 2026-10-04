package com.futspring.backend.service;

import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Team;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyTeamManagementService {

    private final UserAuthenticationHelper userAuthHelper;
    private final DailyRepository dailyRepository;
    private final TeamRepository teamRepository;
    private final DailyDTOMapper dailyDTOMapper;

    private static final Set<String> LOCKED_STATUSES = Set.of("IN_COURSE", "FINISHED", "CANCELED");

    @Transactional
    public List<DailyDetailDTO.TeamDTO> sortTeams(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can sort teams");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot sort teams for a daily with status " + daily.getStatus());
        }

        // Group by star rating, shuffle within each tier, then flatten (desc by stars)
        List<User> players = daily.getConfirmedPlayers().stream()
                .collect(Collectors.groupingBy(User::getStars))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.<Integer, List<User>>comparingByKey().reversed())
                .flatMap(e -> {
                    Collections.shuffle(e.getValue());
                    return e.getValue().stream();
                })
                .collect(Collectors.toList());

        int numberOfTeams = daily.getPelada().getNumberOfTeams();
        int playersPerTeam = daily.getPelada().getPlayersPerTeam();
        int required = numberOfTeams * playersPerTeam;

        if (players.size() != required) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                "Exactly " + required + " confirmed players are required (" +
                numberOfTeams + " teams × " + playersPerTeam + " players). " +
                "Currently: " + players.size());
        }

        // Delete existing teams
        List<Team> existingTeams = teamRepository.findByDaily(daily);
        for (Team t : existingTeams) {
            t.getPlayers().clear();
            teamRepository.save(t);
        }
        teamRepository.deleteAll(existingTeams);

        // Create N teams dynamically
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            teams.add(teamRepository.save(
                Team.builder().daily(daily).name("Team " + i).build()));
        }

        // Karmarkar-Karp (LPT): assign each player (highest stars first)
        // to the non-full team with the lowest current total — minimises imbalance
        int[] totals = new int[numberOfTeams];
        int[] sizes  = new int[numberOfTeams];
        for (User player : players) {
            int minIdx = -1, minTotal = Integer.MAX_VALUE;
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

        return teams.stream().map(dailyDTOMapper::buildTeamDTO).collect(Collectors.toList());
    }

    @Transactional
    public List<DailyDetailDTO.TeamDTO> swapPlayers(Long id, Long player1Id, Long player2Id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can swap players");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                "Cannot swap players for a daily with status " + daily.getStatus());
        }

        List<Team> teams = teamRepository.findByDaily(daily);

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

        if (team1 == null || player1 == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Player 1 is not on any team in this daily");
        }
        if (team2 == null || player2 == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Player 2 is not on any team in this daily");
        }

        team1.getPlayers().remove(player1);
        team2.getPlayers().remove(player2);
        team1.getPlayers().add(player2);
        team2.getPlayers().add(player1);

        teamRepository.save(team1);
        teamRepository.save(team2);

        return teams.stream().map(dailyDTOMapper::buildTeamDTO).collect(Collectors.toList());
    }

    @Transactional
    public DailyDetailDTO.TeamDTO updateTeamName(Long dailyId, Long teamId, String name, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        Daily daily = dailyRepository.findById(dailyId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Team not found"));

        if (!team.getDaily().getId().equals(dailyId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Team not found in this daily");
        }

        if (!team.getPlayers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only team members can rename their team");
        }

        if (name == null || name.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Team name must not be blank");
        }

        team.setName(name.trim());
        teamRepository.save(team);
        return dailyDTOMapper.buildTeamDTO(team);
    }

    @Transactional
    public DailyDetailDTO.TeamDTO updateTeamColor(Long dailyId, Long teamId, String color, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        Daily daily = dailyRepository.findById(dailyId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Team not found"));

        if (!team.getDaily().getId().equals(dailyId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Team not found in this daily");
        }

        if (!team.getPlayers().contains(caller) && !pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only team members or admins can change a team's color");
        }

        if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Color must be a valid 6-digit hex (e.g. #3b82f6)");
        }

        team.setColor(color);
        teamRepository.save(team);
        return dailyDTOMapper.buildTeamDTO(team);
    }

    void clearTeams(Daily daily) {
        List<Team> teams = teamRepository.findByDaily(daily);
        for (Team team : teams) {
            team.getPlayers().clear();
            teamRepository.save(team);
        }
        teamRepository.deleteAll(teams);
    }
}
