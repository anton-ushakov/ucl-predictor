package com.example.predictor.repository;

import com.example.predictor.domain.TournamentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentQuestionRepository extends JpaRepository<TournamentQuestion, Long> {

    List<TournamentQuestion> findByGroupIdOrderByClosesAtDescIdDesc(Long groupId);
}
