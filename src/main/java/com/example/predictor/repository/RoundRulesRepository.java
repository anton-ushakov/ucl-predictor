package com.example.predictor.repository;

import com.example.predictor.domain.RoundRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface RoundRulesRepository extends JpaRepository<RoundRules, Long> {

    Optional<RoundRules> findByGroupIdAndRoundWeek(Long groupId, LocalDate roundWeek);

    /** Фиксирует правила тура. Если они уже зафиксированы, ничего не делает. */
    @Modifying
    @Query(value = "insert into round_rules (group_id, round_week, lock_minutes_before, tiebreakers, reveal_mode, "
            + "count_extra_time, count_penalties, submission_mode, "
            + "exact_score_points, goal_diff_points, outcome_points) "
            + "values (:groupId, :week, :lockMinutes, :tiebreakers, :revealMode, :countExtraTime, "
            + ":countPenalties, :submissionMode, :exactScorePoints, :goalDiffPoints, :outcomePoints) "
            + "on conflict (group_id, round_week) do nothing", nativeQuery = true)
    int freeze(@Param("groupId") Long groupId,
               @Param("week") LocalDate week,
               @Param("lockMinutes") int lockMinutes,
               @Param("tiebreakers") String tiebreakers,
               @Param("revealMode") String revealMode,
               @Param("countExtraTime") boolean countExtraTime,
               @Param("countPenalties") boolean countPenalties,
               @Param("submissionMode") String submissionMode,
               @Param("exactScorePoints") int exactScorePoints,
               @Param("goalDiffPoints") int goalDiffPoints,
               @Param("outcomePoints") int outcomePoints);
}
