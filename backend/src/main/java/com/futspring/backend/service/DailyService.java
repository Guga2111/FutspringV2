package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.UserRepository;
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
}
