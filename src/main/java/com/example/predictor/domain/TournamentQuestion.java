package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Вопрос на турнир: длинный прогноз, который проверяется в конце. */
@Entity
@Table(name = "tournament_questions")
public class TournamentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String hint;

    @Column(nullable = false)
    private int points;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    /** Неделя, к которой относится вопрос: по ней он попадает в нужный сезон. */
    @Column(name = "round_week", nullable = false)
    private LocalDate roundWeek;

    @Column(name = "correct_answer", length = 200)
    private String correctAnswer;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected TournamentQuestion() {
    }

    public TournamentQuestion(Long groupId, String title, String hint, int points, Instant closesAt,
                              LocalDate roundWeek, Instant createdAt, Long createdBy) {
        this.groupId = groupId;
        this.title = title;
        this.hint = hint;
        this.points = points;
        this.closesAt = closesAt;
        this.roundWeek = roundWeek;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void settle(String correctAnswer, Instant at) {
        this.correctAnswer = correctAnswer;
        this.settledAt = at;
    }

    public boolean isSettled() {
        return settledAt != null;
    }

    public boolean isOpen(Instant now) {
        return settledAt == null && now.isBefore(closesAt);
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getTitle() { return title; }
    public String getHint() { return hint; }
    public int getPoints() { return points; }
    public Instant getClosesAt() { return closesAt; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public String getCorrectAnswer() { return correctAnswer; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
}
