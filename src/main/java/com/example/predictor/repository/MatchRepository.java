package com.example.predictor.repository;

import com.example.predictor.domain.FootballMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface MatchRepository extends JpaRepository<FootballMatch, Long> {

    List<FootballMatch> findByRoundWeekOrderByKickoffAtAscIdAsc(LocalDate roundWeek);

    List<FootballMatch> findByRoundWeekBetweenOrderByKickoffAtAscIdAsc(LocalDate from, LocalDate to);

    List<FootballMatch> findByExternalIdIn(Collection<Long> externalIds);

    @Query("select distinct m.roundWeek from FootballMatch m order by m.roundWeek")
    List<LocalDate> findAllRoundWeeks();
}
