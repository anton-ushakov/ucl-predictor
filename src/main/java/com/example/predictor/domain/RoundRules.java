package com.example.predictor.domain;

import com.example.predictor.scoring.Scoring;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Правила тура, зафиксированные для группы в момент, когда сохранён первый прогноз на этот тур.
 * После этого изменение настроек группы на этот тур уже не влияет.
 */
@Entity
@Table(name = "round_rules")
public class RoundRules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "round_week", nullable = false, updatable = false)
    private LocalDate roundWeek;

    @Column(name = "lock_minutes_before", nullable = false, updatable = false)
    private int lockMinutesBefore;

    @Column(nullable = false, updatable = false, length = 200)
    private String tiebreakers;

    @Enumerated(EnumType.STRING)
    @Column(name = "reveal_mode", nullable = false, updatable = false, length = 20)
    private RevealMode revealMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_mode", nullable = false, updatable = false, length = 20)
    private SubmissionMode submissionMode;

    @Column(name = "exact_score_points", nullable = false, updatable = false)
    private int exactScorePoints;

    @Column(name = "goal_diff_points", nullable = false, updatable = false)
    private int goalDiffPoints;

    @Column(name = "outcome_points", nullable = false, updatable = false)
    private int outcomePoints;

    @Column(name = "count_extra_time", nullable = false, updatable = false)
    private boolean countExtraTime;

    @Column(name = "count_penalties", nullable = false, updatable = false)
    private boolean countPenalties;

    @Column(name = "frozen_at", nullable = false, updatable = false)
    private Instant frozenAt;

    protected RoundRules() {
    }

    public ScoreBasis scoreBasis() {
        return new ScoreBasis(countExtraTime, countPenalties);
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public int getLockMinutesBefore() { return lockMinutesBefore; }
    public String getTiebreakers() { return tiebreakers; }
    public RevealMode getRevealMode() { return revealMode; }
    public SubmissionMode getSubmissionMode() { return submissionMode; }

    public Scoring scoring() {
        return new Scoring(exactScorePoints, goalDiffPoints, outcomePoints);
    }
    public Instant getFrozenAt() { return frozenAt; }
}
