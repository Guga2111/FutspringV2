package com.futspring.backend.domain.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Daily at 04:00 (server time): deletes reset tokens that expired or were used more than a day ago
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetTokenCleanup {

    private final PasswordResetTokenRepository tokenRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteStaleTokens() {
        int deleted = tokenRepository.deleteStale(LocalDateTime.now().minusDays(1));
        log.info("Deleted {} stale password reset tokens", deleted);
    }
}
