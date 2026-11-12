package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Решение владельца группы по матчу, у которого нет полных данных (см. MatchResolution). */
@Entity
@Table(name = "score_decisions")
public class ScoreDecision {

    public enum Choice {
        /** Предложено посчитать матч только по основному времени. */
        PROPOSED,
        /** Владелец не согласен: матч не учитывается. */
        DECLINED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Choice status;

    @Column(name = "proposed_by", nullable = false, updatable = false)
    private Long proposedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ScoreDecision() {
    }

    public ScoreDecision(Long groupId, Long matchId, Choice status, Long proposedBy, Instant createdAt) {
        this.groupId = groupId;
        this.matchId = matchId;
        this.status = status;
        this.proposedBy = proposedBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getMatchId() { return matchId; }
    public Choice getStatus() { return status; }
    public Long getProposedBy() { return proposedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
