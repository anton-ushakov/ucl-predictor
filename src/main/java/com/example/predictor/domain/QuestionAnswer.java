package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Ответ участника на вопрос турнира. */
@Entity
@Table(name = "question_answers")
public class QuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String answer;

    /** null — ещё не проверяли. */
    @Column(name = "correct")
    private Boolean correct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuestionAnswer() {
    }

    public QuestionAnswer(Long questionId, Long userId, String answer, Instant createdAt) {
        this.questionId = questionId;
        this.userId = userId;
        this.answer = answer;
        this.createdAt = createdAt;
    }

    /** Передумал, пока приём открыт. */
    public void change(String answer, Instant at) {
        this.answer = answer;
        this.createdAt = at;
    }

    public void mark(boolean correct) {
        this.correct = correct;
    }

    public boolean isCorrect() {
        return Boolean.TRUE.equals(correct);
    }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public Long getUserId() { return userId; }
    public String getAnswer() { return answer; }
    public Boolean getCorrect() { return correct; }
    public Instant getCreatedAt() { return createdAt; }
}
