package com.example.predictor.repository;

import com.example.predictor.domain.RoundResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RoundResultRepository extends JpaRepository<RoundResult, Long> {

    boolean existsByGroupIdAndRoundWeek(Long groupId, LocalDate roundWeek);

    List<RoundResult> findByGroupIdAndRoundWeekOrderByPlaceAscUserIdAsc(Long groupId, LocalDate roundWeek);

    List<RoundResult> findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(Long groupId);

    /** Туры группы, итоги которых уже зафиксированы. */
    @Query("select distinct r.roundWeek from RoundResult r where r.groupId = :groupId")
    List<LocalDate> findFinalizedWeeks(@Param("groupId") Long groupId);
}
