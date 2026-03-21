package com.futspring.backend.service;

import com.futspring.backend.dto.RankingDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Ranking;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.RankingRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final PeladaRepository peladaRepository;
    private final UserRepository userRepository;
    private final RankingRepository rankingRepository;

    @Transactional(readOnly = true)
    public List<RankingDTO> getRanking(Long peladaId, String callerEmail) {
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        List<Ranking> rankings = rankingRepository.findByPelada(pelada);
        Map<Long, Ranking> rankingByUserId = rankings.stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), r -> r));

        return pelada.getMembers().stream()
                .map(member -> {
                    Ranking ranking = rankingByUserId.get(member.getId());
                    return ranking != null ? RankingDTO.fromRanking(ranking) : RankingDTO.fromUser(member);
                })
                .sorted((a, b) -> b.getGoals() != a.getGoals()
                        ? b.getGoals() - a.getGoals()
                        : b.getAssists() - a.getAssists())
                .collect(Collectors.toList());
    }
}
