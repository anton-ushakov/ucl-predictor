package com.example.predictor.repository;

import com.example.predictor.domain.PollVote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    List<PollVote> findByPollIdIn(List<Long> pollIds);

    Optional<PollVote> findByPollIdAndUserId(Long pollId, Long userId);
}
