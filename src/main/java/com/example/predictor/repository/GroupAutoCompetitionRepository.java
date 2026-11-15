package com.example.predictor.repository;

import com.example.predictor.domain.GroupAutoCompetition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupAutoCompetitionRepository extends JpaRepository<GroupAutoCompetition, Long> {

    List<GroupAutoCompetition> findByGroupId(Long groupId);

    Optional<GroupAutoCompetition> findByGroupIdAndCompetitionId(Long groupId, Long competitionId);
}
