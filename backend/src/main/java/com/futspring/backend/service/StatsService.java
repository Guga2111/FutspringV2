package com.futspring.backend.service;

import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.StatsRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final StatsRepository statsRepository;

    @Transactional(readOnly = true)
    public StatsDTO getStats(Long userId, String callerEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        return statsRepository.findByUser(user)
                .map(StatsDTO::fromStats)
                .orElse(StatsDTO.fromUser(user));
    }
}
