package com.example.predictor.repository;

import com.example.predictor.domain.GroupPoll;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupPollRepository extends JpaRepository<GroupPoll, Long> {

    List<GroupPoll> findByGroupIdOrderByIdDesc(Long groupId);

    /** Сколько идущих голосований ждёт голоса этого участника: по этому числу значок на кнопке. */
    @Query("select count(p) from GroupPoll p where p.groupId = :groupId and p.closedAt is null "
            + "and (p.closesAt is null or p.closesAt > :now) "
            + "and not exists (select v from PollVote v where v.pollId = p.id "
            + "and v.userId = :userId)")
    long countWaitingFor(@Param("groupId") Long groupId, @Param("userId") Long userId,
                         @Param("now") Instant now);
}
