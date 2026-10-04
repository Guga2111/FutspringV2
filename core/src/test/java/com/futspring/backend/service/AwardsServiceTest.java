package com.futspring.backend.service;

import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.helper.PeladaAccessHelper;
import com.futspring.backend.repository.*;
import com.futspring.backend.dto.PeladaAwardsDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyAward;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.PeladaRepository;
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
class AwardsServiceTest {

    @Mock
    PeladaRepository peladaRepository;
    @Mock
    UserAuthenticationHelper userAuthHelper;
    @Mock
    DailyAwardRepository dailyAwardRepository;

    AwardsService awardsService;

    User admin;
    User player1;
    User player2;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        awardsService = new AwardsService(peladaRepository, userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyAwardRepository);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        player1 = User.builder().id(2L).email("player1@example.com").username("player1").password("hash").build();
        player2 = User.builder().id(3L).email("player2@example.com").username("player2").password("hash").build();
        outsider = User.builder().id(4L).email("out@example.com").username("out").password("hash").build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .members(new HashSet<>(Set.of(admin, player1, player2)))
                .admins(new HashSet<>(Set.of(admin)))
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
    }

    @Test
    void getAwards_returns4Categories() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(Collections.emptyList()));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        assertThat(result.getTotalCategories()).isEqualTo(4);
        assertThat(result.getCategories()).hasSize(4);
    }

    @Test
    void getAwards_emptyPelada_allCategoriesEmpty() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(Collections.emptyList()));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        result.getCategories().forEach(cat ->
                assertThat(cat.getTopWinners()).isEmpty());
    }

    @Test
    void getAwards_correctCountsPerCategory() {
        Daily daily = Daily.builder().id(1L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();

        DailyAward award1 = DailyAward.builder()
                .id(1L)
                .daily(daily)
                .puskasWinners(List.of(player1))
                .wiltballWinners(List.of(player2))
                .artilheiroWinners(List.of(player1))
                .garcomWinners(List.of(player2))
                .build();

        DailyAward award2 = DailyAward.builder()
                .id(2L)
                .daily(daily)
                .puskasWinners(List.of(player1))
                .wiltballWinners(List.of(player1))
                .artilheiroWinners(List.of(player1))
                .garcomWinners(List.of(player1))
                .build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(List.of(award1, award2)));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        PeladaAwardsDTO.AwardCategoryDTO artilheiro = result.getCategories().stream()
                .filter(c -> "ARTILHEIRO".equals(c.getType())).findFirst().orElseThrow();

        assertThat(artilheiro.getTopWinners().get(0).getCount()).isEqualTo(2);
    }

    @Test
    void getAwards_returnsEveryWinner() {
        Daily daily = Daily.builder().id(1L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();

        User u4 = User.builder().id(5L).email("u4@e.com").username("u4").password("h").build();
        User u5 = User.builder().id(6L).email("u5@e.com").username("u5").password("h").build();

        DailyAward award = DailyAward.builder()
                .id(1L)
                .daily(daily)
                .artilheiroWinners(List.of(admin, player1, player2, u4, u5))
                .garcomWinners(Collections.emptyList())
                .build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(List.of(award)));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        PeladaAwardsDTO.AwardCategoryDTO artilheiro = result.getCategories().stream()
                .filter(c -> "ARTILHEIRO".equals(c.getType())).findFirst().orElseThrow();

        // the client shows the top 3 and expands to the rest ("Ver todos" in AwardsTab)
        assertThat(artilheiro.getTopWinners()).hasSize(5);
    }

    @Test
    void getAwards_sortedDescendingByCount() {
        Daily d1 = Daily.builder().id(1L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();
        Daily d2 = Daily.builder().id(2L).pelada(pelada).dailyDate(java.time.LocalDate.now().minusDays(7)).dailyTime("18:00").build();

        DailyAward award1 = DailyAward.builder().id(1L).daily(d1)
                .puskasWinners(List.of(player1)).artilheiroWinners(Collections.emptyList())
                .garcomWinners(Collections.emptyList()).build();
        DailyAward award2 = DailyAward.builder().id(2L).daily(d2)
                .puskasWinners(List.of(player1)).artilheiroWinners(Collections.emptyList())
                .garcomWinners(Collections.emptyList()).build();
        DailyAward award3 = DailyAward.builder().id(3L).daily(d2)
                .puskasWinners(List.of(player2)).artilheiroWinners(Collections.emptyList())
                .garcomWinners(Collections.emptyList()).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(List.of(award1, award2, award3)));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        PeladaAwardsDTO.AwardCategoryDTO puskas = result.getCategories().stream()
                .filter(c -> "PUSKAS".equals(c.getType())).findFirst().orElseThrow();

        // player1 has 2 wins, player2 has 1 — player1 should be first
        assertThat(puskas.getTopWinners().get(0).getUserId()).isEqualTo(2L);
        assertThat(puskas.getTopWinners().get(0).getCount()).isEqualTo(2);
    }

    @Test
    void getAwards_totalAwardsDistributed_sumMatchesCategories() {
        Daily daily = Daily.builder().id(1L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();

        DailyAward award = DailyAward.builder()
                .id(1L)
                .daily(daily)
                .puskasWinners(List.of(player1))
                .wiltballWinners(List.of(player2))
                .artilheiroWinners(List.of(player1))
                .garcomWinners(List.of(player2))
                .build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(List.of(award)));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        int sumFromCategories = result.getCategories().stream()
                .flatMap(c -> c.getTopWinners().stream())
                .mapToInt(PeladaAwardsDTO.AwardWinnerDTO::getCount)
                .sum();
        assertThat(result.getTotalAwardsDistributed()).isEqualTo(sumFromCategories);
    }

    @Test
    void getAwards_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> awardsService.getAwards(10L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAwards_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> awardsService.getAwards(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAwards_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> awardsService.getAwards(10L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getAwards_multipleArtilheiroWinners_allCounted() {
        Daily daily = Daily.builder().id(1L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();

        DailyAward award = DailyAward.builder()
                .id(1L)
                .daily(daily)
                .artilheiroWinners(List.of(player1, player2))
                .garcomWinners(Collections.emptyList())
                .build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyAwardRepository.countWinnersByPelada(10L)).thenReturn(rowsFrom(List.of(award)));

        PeladaAwardsDTO result = awardsService.getAwards(10L, "admin@example.com");

        PeladaAwardsDTO.AwardCategoryDTO artilheiro = result.getCategories().stream()
                .filter(c -> "ARTILHEIRO".equals(c.getType())).findFirst().orElseThrow();

        assertThat(artilheiro.getTopWinners()).hasSize(2);
    }

    // What DailyAwardRepository.countWinnersByPelada returns for these awards: [category, userId, username, image, count]
    private static List<Object[]> rowsFrom(List<DailyAward> awards) {
        Map<String, Map<User, Integer>> counts = new LinkedHashMap<>();
        for (DailyAward award : awards) {
            count(counts, "ARTILHEIRO", award.getArtilheiroWinners());
            count(counts, "GARCOM", award.getGarcomWinners());
            count(counts, "PUSKAS", award.getPuskasWinners());
            count(counts, "BOLA_MURCHA", award.getWiltballWinners());
        }
        List<Object[]> rows = new ArrayList<>();
        counts.forEach((category, byUser) -> byUser.forEach((user, n) ->
                rows.add(new Object[]{category, user.getId(), user.getUsername(), user.getImage(), (long) n})));
        return rows;
    }

    private static void count(Map<String, Map<User, Integer>> counts, String category, List<User> winners) {
        for (User winner : winners) {
            counts.computeIfAbsent(category, k -> new LinkedHashMap<>()).merge(winner, 1, Integer::sum);
        }
    }
}
