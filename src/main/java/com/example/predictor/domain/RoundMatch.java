package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Матч, который владелец группы отобрал в тур. Прогнозы делаются только на отобранные матчи,
 * и только они участвуют в подсчёте баллов.
 */
@Entity
@Table(name = "round_matches")
public class RoundMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "round_week", nullable = false, updatable = false)
    private LocalDate roundWeek;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    @Column(name = "added_by", nullable = false, updatable = false)
    private Long addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoundMatch() {
    }

    public RoundMatch(Long groupId, LocalDate roundWeek, Long matchId, Long addedBy, Instant createdAt) {
        this.groupId = groupId;
        this.roundWeek = roundWeek;
        this.matchId = matchId;
        this.addedBy = addedBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public Long getMatchId() { return matchId; }
    public Long getAddedBy() { return addedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
