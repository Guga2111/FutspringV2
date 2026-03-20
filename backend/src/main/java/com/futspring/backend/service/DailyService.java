package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyDetailDTO.*;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyService {

    private final DailyRepository dailyRepository;
    private final PeladaRepository peladaRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final LeagueTableEntryRepository leagueTableEntryRepository;
    private final DailyAwardRepository dailyAwardRepository;

    @Transactional
    public DailyListItemDTO createDaily(Long peladaId, CreateDailyRequestDTO request, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can create dailies");
        }

        Daily daily = Daily.builder()
                .pelada(pelada)
                .dailyDate(request.getDailyDate())
                .dailyTime(request.getDailyTime())
                .build();

        Daily saved = dailyRepository.save(daily);
        return DailyListItemDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DailyListItemDTO> getDailiesForPelada(Long peladaId, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        return dailyRepository.findByPeladaOrderByDailyDateDesc(pelada).stream()
                .map(DailyListItemDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DailyDetailDTO getDailyDetail(Long id, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        List<PlayerDTO> confirmedPlayers = daily.getConfirmedPlayers().stream()
                .map(u -> PlayerDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .image(u.getImage())
                        .stars(u.getStars())
                        .position(u.getPosition())
                        .build())
                .collect(Collectors.toList());

        List<Team> teams = teamRepository.findByDaily(daily);
        List<TeamDTO> teamDTOs = teams.stream()
                .map(team -> {
                    List<PlayerDTO> players = team.getPlayers().stream()
                            .map(u -> PlayerDTO.builder()
                                    .id(u.getId())
                                    .username(u.getUsername())
                                    .image(u.getImage())
                                    .stars(u.getStars())
                                    .position(u.getPosition())
                                    .build())
                            .collect(Collectors.toList());
                    int totalStars = players.stream().mapToInt(PlayerDTO::getStars).sum();
                    return TeamDTO.builder()
                            .id(team.getId())
                            .name(team.getName())
                            .totalStars(totalStars)
                            .players(players)
                            .build();
                })
                .collect(Collectors.toList());

        List<Match> matches = matchRepository.findByDaily(daily);
        List<MatchDTO> matchDTOs = matches.stream()
                .map(m -> MatchDTO.builder()
                        .id(m.getId())
                        .team1Id(m.getTeam1().getId())
                        .team1Name(m.getTeam1().getName())
                        .team2Id(m.getTeam2().getId())
                        .team2Name(m.getTeam2().getName())
                        .team1Score(m.getTeam1Score())
                        .team2Score(m.getTeam2Score())
                        .winnerId(m.getWinner() != null ? m.getWinner().getId() : null)
                        .build())
                .collect(Collectors.toList());

        List<UserDailyStats> statsEntities = userDailyStatsRepository.findByDaily(daily);
        List<UserDailyStatsDTO> playerStats = statsEntities.stream()
                .map(s -> UserDailyStatsDTO.builder()
                        .userId(s.getUser().getId())
                        .username(s.getUser().getUsername())
                        .goals(s.getGoals())
                        .assists(s.getAssists())
                        .matchesPlayed(s.getMatchesPlayed())
                        .wins(s.getWins())
                        .build())
                .collect(Collectors.toList());

        List<LeagueTableEntry> leagueEntities = leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily);
        List<LeagueTableEntryDTO> leagueTable = leagueEntities.stream()
                .map(e -> LeagueTableEntryDTO.builder()
                        .teamId(e.getTeam().getId())
                        .teamName(e.getTeam().getName())
                        .position(e.getPosition())
                        .wins(e.getWins())
                        .draws(e.getDraws())
                        .losses(e.getLosses())
                        .goalsFor(e.getGoalsFor())
                        .goalsAgainst(e.getGoalsAgainst())
                        .goalDiff(e.getGoalsFor() - e.getGoalsAgainst())
                        .points(e.getPoints())
                        .build())
                .collect(Collectors.toList());

        AwardDTO award = dailyAwardRepository.findByDaily(daily)
                .map(a -> AwardDTO.builder()
                        .puskasWinnerId(a.getPuskasWinner() != null ? a.getPuskasWinner().getId() : null)
                        .puskasWinnerName(a.getPuskasWinner() != null ? a.getPuskasWinner().getUsername() : null)
                        .wiltballWinnerId(a.getWiltballWinner() != null ? a.getWiltballWinner().getId() : null)
                        .wiltballWinnerName(a.getWiltballWinner() != null ? a.getWiltballWinner().getUsername() : null)
                        .build())
                .orElse(null);

        return DailyDetailDTO.builder()
                .id(daily.getId())
                .dailyDate(daily.getDailyDate())
                .dailyTime(daily.getDailyTime())
                .status(daily.getStatus())
                .isFinished(daily.isFinished())
                .championImage(daily.getChampionImage())
                .confirmedPlayers(confirmedPlayers)
                .teams(teamDTOs)
                .matches(matchDTOs)
                .playerStats(playerStats)
                .leagueTable(leagueTable)
                .award(award)
                .peladaId(pelada.getId())
                .peladaName(pelada.getName())
                .build();
    }
}
