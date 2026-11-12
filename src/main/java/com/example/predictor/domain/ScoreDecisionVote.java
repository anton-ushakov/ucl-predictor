package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Голос участника тура по предложению владельца. Изменить голос нельзя. */
@Entity
@Table(name = "score_decision_votes")
public class ScoreDecisionVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_id", nullable = false, updatable = false)
    private Long decisionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private boolean agree;

    @Column(name = "voted_at", nullable = false, updatable = false)
    private Instant votedAt;

    protected ScoreDecisionVote() {
    }

    public ScoreDecisionVote(Long decisionId, Long userId, boolean agree, Instant votedAt) {
        this.decisionId = decisionId;
        this.userId = userId;
        this.agree = agree;
        this.votedAt = votedAt;
    }

    public Long getDecisionId() { return decisionId; }
    public Long getUserId() { return userId; }
    public boolean isAgree() { return agree; }
    public Instant getVotedAt() { return votedAt; }
}
