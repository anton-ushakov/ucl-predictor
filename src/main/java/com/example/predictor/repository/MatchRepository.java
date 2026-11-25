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

    /** Номера матчей у разных источников свои, поэтому ищем парой. */
    List<FootballMatch> findBySourceAndExternalIdIn(String source, Collection<Long> externalIds);

    /** Сколько матчей этих турниров сейчас могут идти: начались недавно и не завершены. */
    @Query("select count(m) from FootballMatch m where m.competition.id in :competitionIds "
            + "and m.kickoffAt between :from and :to "
            + "and (m.status = com.example.predictor.domain.MatchStatus.SCHEDULED "
            + "or m.status = com.example.predictor.domain.MatchStatus.LIVE)")
    long countPlaying(@Param("competitionIds") Collection<Long> competitionIds,
                      @Param("from") java.time.Instant from,
                      @Param("to") java.time.Instant to);

    @Query("select distinct m.roundWeek from FootballMatch m order by m.roundWeek")
    List<LocalDate> findAllRoundWeeks();

    /** Сколько загруженных матчей недели начнётся в указанный промежуток. */
    @Query("select count(m) from FootballMatch m where m.roundWeek = :week "
            + "and m.ownerGroupId is null and m.status = :status "
            + "and m.kickoffAt between :from and :to")
    long countUpcoming(@Param("week") LocalDate week,
                       @Param("status") com.example.predictor.domain.MatchStatus status,
                       @Param("from") java.time.Instant from,
                       @Param("to") java.time.Instant to);

    /** Когда начнётся первый загруженный матч недели из промежутка. null — таких нет. */
    @Query("select min(m.kickoffAt) from FootballMatch m where m.roundWeek = :week "
            + "and m.ownerGroupId is null and m.status = :status "
            + "and m.kickoffAt between :from and :to")
    java.time.Instant firstKickoff(@Param("week") LocalDate week,
                                   @Param("status") com.example.predictor.domain.MatchStatus status,
                                   @Param("from") java.time.Instant from,
                                   @Param("to") java.time.Instant to);

    /** Оригинальные названия команд-хозяев во всех матчах, доступных группе. */
    @Query("select distinct m.homeTeam from FootballMatch m "
            + "where m.ownerGroupId is null or m.ownerGroupId = :groupId")
    List<String> findHomeTeamNames(@Param("groupId") Long groupId);

    /** То же для команд-гостей. */
    @Query("select distinct m.awayTeam from FootballMatch m "
            + "where m.ownerGroupId is null or m.ownerGroupId = :groupId")
    List<String> findAwayTeamNames(@Param("groupId") Long groupId);

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

    /** Ближайшие несыгранные матчи: для них берём справки вроде личных встреч. */
    List<FootballMatch> findByStatusAndKickoffAtBetweenOrderByKickoffAtAsc(
            com.example.predictor.domain.MatchStatus status, java.time.Instant from,
            java.time.Instant to);

    /**
     * Турниры, в которых прямо сейчас кто-то играет: матч начался, а итог ещё не пришёл.
     * По ним счёт спрашивается часто, по остальным — раз в полчаса, как и раньше.
     */
    @Query("""
            select distinct m.competition.id from FootballMatch m
             where m.competition is not null
               and m.status in (com.example.predictor.domain.MatchStatus.SCHEDULED,
                                com.example.predictor.domain.MatchStatus.LIVE)
               and m.kickoffAt between :from and :to
            """)
    List<Long> findCompetitionsPlaying(@Param("from") java.time.Instant from,
                                       @Param("to") java.time.Instant to);

    /**
     * Сыгранные матчи этих команд, свежие сверху: по ним считается форма, когда источник
     * её не присылает.
     */
    @Query("select m from FootballMatch m where m.status = com.example.predictor.domain.MatchStatus.FINISHED "
            + "and m.homeScore is not null "
            + "and (m.homeTeam in :teams or m.awayTeam in :teams) "
            + "order by m.kickoffAt desc")
    List<FootballMatch> findFinishedOf(@Param("teams") Collection<String> teams);
}
