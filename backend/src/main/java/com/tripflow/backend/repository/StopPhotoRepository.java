package com.tripflow.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.domain.StopPhoto;

public interface StopPhotoRepository extends JpaRepository<StopPhoto, Long> {
    List<StopPhoto> findByStopIdOrderByCreatedAtAsc(Long stopId);

    Optional<StopPhoto> findByIdAndStopId(Long id, Long stopId);

    /**
     * Batch fetch across every stop on a feed page (SOCIAL-01 Pitfall 2) — collect every
     * stop id across the whole page first, call this once, group by stop id in the mapper.
     * {@link #findByStopIdOrderByCreatedAtAsc} stays single-stop only; {@code StopPhotoService}'s
     * detail-view path continues to use that method unchanged.
     */
    List<StopPhoto> findByStopIdInOrderByCreatedAtAsc(List<Long> stopIds);
}