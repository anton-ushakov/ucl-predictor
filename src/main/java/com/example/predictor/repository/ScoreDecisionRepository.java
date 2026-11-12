package com.example.predictor.repository;

import com.example.predictor.domain.ScoreDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScoreDecisionRepository extends JpaRepository<ScoreDecision, Long> {

    List<ScoreDecision> findByGroupIdAndMatchIdIn(Long groupId, Collection<Long> matchIds);
}
