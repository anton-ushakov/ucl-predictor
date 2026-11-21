package com.example.predictor.repository;

import com.example.predictor.domain.MatchHeadToHead;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeadToHeadRepository extends JpaRepository<MatchHeadToHead, Long> {

    List<MatchHeadToHead> findByMatchIdIn(List<Long> matchIds);

    boolean existsByMatchId(Long matchId);
}
