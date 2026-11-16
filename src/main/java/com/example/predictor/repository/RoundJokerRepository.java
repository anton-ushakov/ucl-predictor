package com.example.predictor.repository;

import com.example.predictor.domain.RoundJoker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoundJokerRepository extends JpaRepository<RoundJoker, Long> {

    List<RoundJoker> findByGroupIdAndRoundWeek(Long groupId, LocalDate roundWeek);

    Optional<RoundJoker> findByGroupIdAndRoundWeekAndUserId(Long groupId, LocalDate roundWeek, Long userId);
}
