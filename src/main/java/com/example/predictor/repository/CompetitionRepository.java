package com.example.predictor.repository;

import com.example.predictor.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    List<Competition> findByEnabledTrueOrderBySortOrderAscNameAsc();

    /** Турниры, матчи которых загружаются из источника. */
    List<Competition> findBySyncedTrueAndEnabledTrueOrderBySortOrderAsc();

    /** Турниры одного источника, которые загружаются сами. */
    List<Competition> findBySourceAndSyncedTrueAndEnabledTrueOrderBySortOrderAsc(String source);

    Optional<Competition> findByCode(String code);
}
