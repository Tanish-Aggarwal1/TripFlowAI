package com.tripflow.backend.schedule;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reclaims {@code refresh_tokens} rows that are past their own expiry. Every login inserts a
 * row and every rotation inserts another without removing the one it redeemed, so a single
 * continuously-open tab writes roughly a hundred rows a day and nothing ever took them out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredRefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 30 3 * * *") // daily, staggered off the orphan-place sweep at 03:00
    @Transactional
    public void deleteExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
    }
}
