package com.example.predictor.repository;

import com.example.predictor.domain.GroupSeason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupSeasonRepository extends JpaRepository<GroupSeason, Long> {

    List<GroupSeason> findByGroupIdOrderByStartWeekAsc(Long groupId);
}
