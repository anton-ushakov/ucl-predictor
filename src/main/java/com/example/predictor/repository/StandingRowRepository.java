package com.example.predictor.repository;

import com.example.predictor.domain.StandingRowEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandingRowRepository extends JpaRepository<StandingRowEntity, Long> {

    List<StandingRowEntity> findByCompetitionIdOrderByGroupLabelAscPositionAsc(Long competitionId);

    List<StandingRowEntity> findByCompetitionIdIn(List<Long> competitionIds);

    void deleteByCompetitionId(Long competitionId);
}
