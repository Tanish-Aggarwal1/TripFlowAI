package com.tripflow.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reclaims {@code places} rows with no referencing stop (SCRUM-216). No code path ever
 * deletes a Place directly — deleting a trip cascades to its stops but leaves every
 * referenced Place behind — so orphans accumulate unboundedly without this job.
 *
 * <p>V5__cleanup_orphan_places.sql performs a one-time cleanup of orphans that had already
 * accumulated before this job existed; this runs the same condition on an ongoing schedule.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanPlaceCleanupJob {

    private final PlaceRepository placeRepository;

    @Scheduled(cron = "0 0 3 * * *") // daily at 03:00 server time — low-traffic window
    @Transactional
    public void cleanupOrphanPlaces() {
        int deleted = placeRepository.deleteOrphans();
        if (deleted > 0) {
            log.info("Deleted {} orphaned place(s) with no referencing stop", deleted);
        }
    }
}
