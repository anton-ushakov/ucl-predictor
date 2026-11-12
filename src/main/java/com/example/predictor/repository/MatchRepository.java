package com.example.predictor.repository;

import com.example.predictor.domain.FootballMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface MatchRepository extends JpaRepository<FootballMatch, Long> {

    List<FootballMatch> findByRoundWeekOrderByKickoffAtAscIdAsc(LocalDate roundWeek);

    List<FootballMatch> findByRoundWeekBetweenOrderByKickoffAtAscIdAsc(LocalDate from, LocalDate to);

    List<FootballMatch> findByExternalIdIn(Collection<Long> externalIds);

    @Query("select distinct m.roundWeek from FootballMatch m order by m.roundWeek")
    List<LocalDate> findAllRoundWeeks();

    /**
     * Матчи турнира за промежуток недель, которые можно предложить группе: загруженные из
     * football-data.org (ownerGroupId пуст) и ручные матчи самой этой группы.
     */
    @Query("""
            select m from FootballMatch m
             where m.competition.id = :competitionId
               and m.roundWeek between :from and :to
               and (m.ownerGroupId is null or m.ownerGroupId = :groupId)
             order by m.kickoffAt, m.id
            """)
    List<FootballMatch> findForSelection(@Param("competitionId") Long competitionId,
                                         @Param("groupId") Long groupId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
