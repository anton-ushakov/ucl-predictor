package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Джокер участника на тур: матч, за который баллы считаются вдвойне. */
@Entity
@Table(name = "round_jokers")
public class RoundJoker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "round_week", nullable = false, updatable = false)
    private LocalDate roundWeek;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoundJoker() {
    }

    public RoundJoker(Long groupId, LocalDate roundWeek, Long userId, Long matchId, Instant createdAt) {
        this.groupId = groupId;
        this.roundWeek = roundWeek;
        this.userId = userId;
        this.matchId = matchId;
        this.createdAt = createdAt;
    }

    /** Передумал до закрытия приёма: джокер переносится на другой матч. */
    public void moveTo(Long matchId, Instant at) {
        this.matchId = matchId;
        this.createdAt = at;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public Long getUserId() { return userId; }
    public Long getMatchId() { return matchId; }
    public Instant getCreatedAt() { return createdAt; }
}
