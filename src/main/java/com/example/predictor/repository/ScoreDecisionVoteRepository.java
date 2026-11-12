package com.example.predictor.repository;

import com.example.predictor.domain.ScoreDecisionVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScoreDecisionVoteRepository extends JpaRepository<ScoreDecisionVote, Long> {

    List<ScoreDecisionVote> findByDecisionIdIn(Collection<Long> decisionIds);
}
