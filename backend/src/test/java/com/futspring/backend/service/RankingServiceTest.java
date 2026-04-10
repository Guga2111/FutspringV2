package com.futspring.backend.service;

import com.futspring.backend.dto.RankingDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Ranking;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    PeladaRepository peladaRepository;
    @Mock
    UserAuthenticationHelper userAuthHelper;
    @Mock
    RankingRepository rankingRepository;

    RankingService rankingService;

    User admin;
    User player;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(peladaRepository, userAuthHelper, rankingRepository);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").stars(4).build();
        player = User.builder().id(2L).email("player@example.com").username("player").password("hash").stars(3).build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").stars(3).build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .members(new HashSet<>(Set.of(admin, player)))
                .admins(new HashSet<>(Set.of(admin)))
                .build();
    }

    @Test
    void getRanking_withRankingRecord_returnsCorrectValues() {
        Ranking ranking = Ranking.builder()
                .id(1L)
                .pelada(pelada)
                .user(admin)
                .goals(10)
                .assists(5)
                .matchesPlayed(20)
                .wins(12)
                .build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPelada(pelada)).thenReturn(List.of(ranking));

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        assertThat(result).isNotEmpty();
        RankingDTO adminRank = result.stream()
                .filter(r -> r.getUserId().equals(1L))
                .findFirst().orElseThrow();
        assertThat(adminRank.getGoals()).isEqualTo(10);
        assertThat(adminRank.getAssists()).isEqualTo(5);
    }

    @Test
    void getRanking_noRankingRecord_returnsDefaultValues() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPelada(pelada)).thenReturn(Collections.emptyList());

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        assertThat(result).hasSize(2); // both members included
        result.forEach(r -> {
            assertThat(r.getGoals()).isEqualTo(0);
            assertThat(r.getAssists()).isEqualTo(0);
        });
    }

    @Test
    void getRanking_sortedByGoalsThenAssists() {
        Ranking r1 = Ranking.builder().id(1L).pelada(pelada).user(admin).goals(5).assists(3).build();
        Ranking r2 = Ranking.builder().id(2L).pelada(pelada).user(player).goals(5).assists(8).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPelada(pelada)).thenReturn(List.of(r1, r2));

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        // Both have 5 goals, player has more assists so should be first
        assertThat(result.get(0).getUserId()).isEqualTo(2L);
        assertThat(result.get(1).getUserId()).isEqualTo(1L);
    }

    @Test
    void getRanking_allMembersIncluded() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPelada(pelada)).thenReturn(Collections.emptyList());

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        // pelada has 2 members: admin and player
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(RankingDTO::getUserId))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getRanking_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> rankingService.getRanking(10L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getRanking_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.getRanking(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getRanking_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> rankingService.getRanking(10L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getRanking_tieInGoals_sortedByAssists() {
        Ranking r1 = Ranking.builder().id(1L).pelada(pelada).user(admin).goals(3).assists(2).build();
        Ranking r2 = Ranking.builder().id(2L).pelada(pelada).user(player).goals(3).assists(5).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPelada(pelada)).thenReturn(List.of(r1, r2));

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        assertThat(result.get(0).getAssists()).isEqualTo(5);
        assertThat(result.get(1).getAssists()).isEqualTo(2);
    }
}
