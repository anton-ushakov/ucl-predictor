package com.example.predictor.repository;

import com.example.predictor.domain.RoundMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoundMatchRepository extends JpaRepository<RoundMatch, Long> {

    List<RoundMatch> findByGroupIdAndRoundWeek(Long groupId, LocalDate roundWeek);

    List<RoundMatch> findByGroupIdAndRoundWeekBetween(Long groupId, LocalDate from, LocalDate to);

    Optional<RoundMatch> findByGroupIdAndMatchId(Long groupId, Long matchId);

    /** Недели, в которых у группы отобран хотя бы один матч. */
    @org.springframework.data.jpa.repository.Query(
            "select distinct r.roundWeek from RoundMatch r where r.groupId = :groupId order by r.roundWeek desc")
    List<LocalDate> findWeeks(@org.springframework.data.repository.query.Param("groupId") Long groupId);
}
