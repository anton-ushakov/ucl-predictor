package com.example.predictor.repository;

import com.example.predictor.domain.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /** Все прогнозы группы на указанные матчи (с авторами). */
    @Query("select p from Prediction p join fetch p.user "
            + "where p.group.id = :groupId and p.match.id in :matchIds")
    List<Prediction> findForMatches(@Param("groupId") Long groupId,
                                    @Param("matchIds") Collection<Long> matchIds);

    /** Все прогнозы группы на матчи тура (с авторами и матчами). */
    @Query("select p from Prediction p join fetch p.user join fetch p.match m "
            + "where p.group.id = :groupId and m.roundWeek = :week")
    List<Prediction> findForRound(@Param("groupId") Long groupId, @Param("week") LocalDate week);

    /** Идентификаторы матчей, на которые пользователь уже сделал прогноз в группе, за период. */
    @Query("select p.match.id from Prediction p "
            + "where p.group.id = :groupId and p.user.id = :userId "
            + "and p.match.roundWeek between :from and :to")
    List<Long> findMatchIdsOfUser(@Param("groupId") Long groupId, @Param("userId") Long userId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Туры за период, на которые в группе есть хотя бы один прогноз. */
    @Query("select distinct m.roundWeek from Prediction p join p.match m "
            + "where p.group.id = :groupId and m.roundWeek between :from and :to")
    List<LocalDate> findWeeksWithPredictions(@Param("groupId") Long groupId,
                                             @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Все пары "группа и тур", где есть прогнозы. */
    @Query("select new com.example.predictor.repository.GroupRound(p.group.id, m.roundWeek) "
            + "from Prediction p join p.match m group by p.group.id, m.roundWeek")
    List<GroupRound> findGroupRounds();
}
