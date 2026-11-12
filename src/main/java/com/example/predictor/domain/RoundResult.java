package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Итог участника в завершённом туре группы (снимок для истории). */
@Entity
@Table(name = "round_results")
public class RoundResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "round_week", nullable = false, updatable = false)
    private LocalDate roundWeek;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private int points;

    @Column(name = "exact_scores", nullable = false, updatable = false)
    private int exactScores;

    @Column(name = "outcome_hits", nullable = false, updatable = false)
    private int outcomeHits;

    @Column(name = "goal_diff_hits", nullable = false, updatable = false)
    private int goalDiffHits;

    @Column(name = "goal_error", nullable = false, updatable = false)
    private int goalError;

    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;

    @Column(nullable = false, updatable = false)
    private int place;

    @Column(nullable = false, updatable = false)
    private boolean winner;

    @Column(nullable = false, updatable = false)
    private boolean loser;

    @Column(nullable = false, updatable = false, length = 200)
    private String tiebreakers;

    @Column(name = "count_extra_time", nullable = false, updatable = false)
    private boolean countExtraTime;

    @Column(name = "count_penalties", nullable = false, updatable = false)
    private boolean countPenalties;

    @Column(name = "finalized_at", nullable = false, updatable = false)
    private Instant finalizedAt;

    protected RoundResult() {
    }

    public RoundResult(Long groupId, LocalDate roundWeek, Long userId, int points, int exactScores,
                       int outcomeHits, int goalDiffHits, int goalError, Instant submittedAt,
                       int place, boolean winner, boolean loser, String tiebreakers,
                       ScoreBasis scoreBasis, Instant finalizedAt) {
        this.countExtraTime = scoreBasis.extraTime();
        this.countPenalties = scoreBasis.penalties();
        this.groupId = groupId;
        this.roundWeek = roundWeek;
        this.userId = userId;
        this.points = points;
        this.exactScores = exactScores;
        this.outcomeHits = outcomeHits;
        this.goalDiffHits = goalDiffHits;
        this.goalError = goalError;
        this.submittedAt = submittedAt;
        this.place = place;
        this.winner = winner;
        this.loser = loser;
        this.tiebreakers = tiebreakers;
        this.finalizedAt = finalizedAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public Long getUserId() { return userId; }
    public int getPoints() { return points; }
    public int getExactScores() { return exactScores; }
    public int getOutcomeHits() { return outcomeHits; }
    public int getGoalDiffHits() { return goalDiffHits; }
    public int getGoalError() { return goalError; }
    public Instant getSubmittedAt() { return submittedAt; }
    public int getPlace() { return place; }
    public boolean isWinner() { return winner; }
    public boolean isLoser() { return loser; }
    public String getTiebreakers() { return tiebreakers; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public ScoreBasis scoreBasis() { return new ScoreBasis(countExtraTime, countPenalties); }
}
