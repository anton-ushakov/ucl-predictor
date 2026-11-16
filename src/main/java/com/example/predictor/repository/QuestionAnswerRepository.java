package com.example.predictor.repository;

import com.example.predictor.domain.QuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, Long> {

    List<QuestionAnswer> findByQuestionIdIn(Collection<Long> questionIds);

    List<QuestionAnswer> findByQuestionId(Long questionId);

    Optional<QuestionAnswer> findByQuestionIdAndUserId(Long questionId, Long userId);
}
