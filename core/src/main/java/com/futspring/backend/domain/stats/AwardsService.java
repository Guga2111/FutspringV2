package com.futspring.backend.domain.stats;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.dto.PeladaAwardsDTO.AwardCategoryDTO;
import com.futspring.backend.domain.stats.dto.PeladaAwardsDTO.AwardWinnerDTO;
import com.futspring.backend.domain.stats.dto.PeladaAwardsDTO;
import com.futspring.backend.domain.stats.repository.DailyAwardRepository;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AwardsService {

    private final PeladaRepository peladaRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyAwardRepository dailyAwardRepository;

    @Transactional(readOnly = true)
    public PeladaAwardsDTO getAwards(Long peladaId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        accessHelper.requireMember(pelada, caller);

        // Every winner of every category with their count, in one grouped query
        Map<String, List<AwardWinnerDTO>> winnersByCategory = new HashMap<>();
        for (Object[] row : dailyAwardRepository.countWinnersByPelada(peladaId)) {
            winnersByCategory.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add(AwardWinnerDTO.builder()
                    .userId(((Number) row[1]).longValue())
                    .username((String) row[2])
                    .userImage((String) row[3])
                    .count(((Number) row[4]).intValue())
                    .build());
        }

        List<AwardCategoryDTO> categories = List.of(
                buildCategory("ARTILHEIRO", "Artilheiro", "Mais gols na sessão", winnersByCategory),
                buildCategory("GARCOM", "Garçom", "Mais assistências na sessão", winnersByCategory),
                buildCategory("PUSKAS", "Puskás", "Gol mais bonito", winnersByCategory),
                buildCategory("BOLA_MURCHA", "Bola Murcha", "Pior desempenho da sessão", winnersByCategory));

        int totalDistributed = categories.stream()
                .flatMap(c -> c.getTopWinners().stream())
                .mapToInt(AwardWinnerDTO::getCount)
                .sum();

        return PeladaAwardsDTO.builder()
                .totalCategories(categories.size())
                .totalAwardsDistributed(totalDistributed)
                .categories(categories)
                .build();
    }

    private AwardCategoryDTO buildCategory(String type, String name, String description,
                                           Map<String, List<AwardWinnerDTO>> winnersByCategory) {
        List<AwardWinnerDTO> winners = new ArrayList<>(winnersByCategory.getOrDefault(type, List.of()));
        winners.sort(Comparator.comparingInt(AwardWinnerDTO::getCount).reversed()
                .thenComparing(AwardWinnerDTO::getUsername, String.CASE_INSENSITIVE_ORDER));
        return AwardCategoryDTO.builder()
                .type(type)
                .name(name)
                .description(description)
                .topWinners(winners)
                .build();
    }
}
