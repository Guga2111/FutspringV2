package com.futspring.backend.service;

import com.futspring.backend.dto.PeladaAwardsDTO;
import com.futspring.backend.dto.PeladaAwardsDTO.AwardCategoryDTO;
import com.futspring.backend.dto.PeladaAwardsDTO.AwardWinnerDTO;
import com.futspring.backend.entity.DailyAward;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwardsService {

    private final PeladaRepository peladaRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final DailyAwardRepository dailyAwardRepository;

    @Transactional(readOnly = true)
    public PeladaAwardsDTO getAwards(Long peladaId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        List<DailyAward> awards = dailyAwardRepository.findAllByPelada(pelada);

        // Count maps per category: userId -> count
        Map<Long, int[]> artilheiroMap = new HashMap<>();
        Map<Long, int[]> garcomMap    = new HashMap<>();
        Map<Long, int[]> puskasMap    = new HashMap<>();
        Map<Long, int[]> bolaMurchaMap = new HashMap<>();

        // User metadata cache: userId -> User
        Map<Long, User> userCache = new HashMap<>();

        for (DailyAward award : awards) {
            for (User u : award.getArtilheiroWinners()) {
                artilheiroMap.computeIfAbsent(u.getId(), k -> new int[]{0})[0]++;
                userCache.putIfAbsent(u.getId(), u);
            }
            for (User u : award.getGarcomWinners()) {
                garcomMap.computeIfAbsent(u.getId(), k -> new int[]{0})[0]++;
                userCache.putIfAbsent(u.getId(), u);
            }
            for (User u : award.getPuskasWinners()) {
                puskasMap.computeIfAbsent(u.getId(), k -> new int[]{0})[0]++;
                userCache.putIfAbsent(u.getId(), u);
            }
            for (User u : award.getWiltballWinners()) {
                bolaMurchaMap.computeIfAbsent(u.getId(), k -> new int[]{0})[0]++;
                userCache.putIfAbsent(u.getId(), u);
            }
        }

        AwardCategoryDTO artilheiro = buildCategory(
                "ARTILHEIRO", "Artilheiro", "Mais gols na sessão", artilheiroMap, userCache);
        AwardCategoryDTO garcom = buildCategory(
                "GARCOM", "Garçom", "Mais assistências na sessão", garcomMap, userCache);
        AwardCategoryDTO puskas = buildCategory(
                "PUSKAS", "Puskás", "Gol mais bonito", puskasMap, userCache);
        AwardCategoryDTO bolaMurcha = buildCategory(
                "BOLA_MURCHA", "Bola Murcha", "Pior desempenho da sessão", bolaMurchaMap, userCache);

        List<AwardCategoryDTO> categories = List.of(artilheiro, garcom, puskas, bolaMurcha);

        int totalDistributed = artilheiro.getTopWinners().stream().mapToInt(AwardWinnerDTO::getCount).sum()
                + garcom.getTopWinners().stream().mapToInt(AwardWinnerDTO::getCount).sum()
                + puskas.getTopWinners().stream().mapToInt(AwardWinnerDTO::getCount).sum()
                + bolaMurcha.getTopWinners().stream().mapToInt(AwardWinnerDTO::getCount).sum();

        return PeladaAwardsDTO.builder()
                .totalCategories(4)
                .totalAwardsDistributed(totalDistributed)
                .categories(categories)
                .build();
    }

    private AwardCategoryDTO buildCategory(String type, String name, String description,
                                           Map<Long, int[]> countMap, Map<Long, User> userCache) {
        List<AwardWinnerDTO> winners = new ArrayList<>();
        for (Map.Entry<Long, int[]> entry : countMap.entrySet()) {
            User u = userCache.get(entry.getKey());
            if (u == null) continue;
            winners.add(AwardWinnerDTO.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .userImage(u.getImage())
                    .count(entry.getValue()[0])
                    .build());
        }
        winners.sort(Comparator.comparingInt(AwardWinnerDTO::getCount).reversed());
        List<AwardWinnerDTO> top3 = winners.size() > 3 ? winners.subList(0, 3) : winners;

        return AwardCategoryDTO.builder()
                .type(type)
                .name(name)
                .description(description)
                .topWinners(new ArrayList<>(top3))
                .build();
    }
}
