package com.example.predictor.repository;

import com.example.predictor.domain.RoundMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface RoundMessageRepository extends JpaRepository<RoundMessage, Long> {

    /** Лента тура от старых сообщений к новым. */
    List<RoundMessage> findByGroupIdAndRoundWeekOrderByIdAsc(Long groupId, LocalDate roundWeek);

    /** Только то, что появилось после уже показанного: так экран догружает новое. */
    List<RoundMessage> findByGroupIdAndRoundWeekAndIdGreaterThanOrderByIdAsc(
            Long groupId, LocalDate roundWeek, Long afterId);

    long countByGroupIdAndRoundWeekAndDeletedAtIsNull(Long groupId, LocalDate roundWeek);

    /** Свежие сообщения всех туров группы: по ним готовятся оповещения. */
    @Query("select m from RoundMessage m where m.groupId = :groupId "
            + "and m.createdAt >= :since and m.deletedAt is null order by m.id")
    List<RoundMessage> findFresh(@Param("groupId") Long groupId, @Param("since") Instant since);
}
