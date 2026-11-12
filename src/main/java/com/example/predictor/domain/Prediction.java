package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Прогноз участника на матч в рамках группы. Неизменяемый: в базе стоит триггер,
 * запрещающий UPDATE, а в коде нет ни одного метода изменения.
 */
@Entity
@Table(name = "predictions",
        uniqueConstraints = @UniqueConstraint(name = "uq_prediction_group_user_match",
                columnNames = {"group_id", "user_id", "match_id"}))
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", updatable = false)
    private FriendGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", updatable = false)
    private FootballMatch match;

    @Column(name = "home_goals", nullable = false, updatable = false)
    private int homeGoals;

    @Column(name = "away_goals", nullable = false, updatable = false)
    private int awayGoals;

    /** Время сохранения. Ставится сервером, с телефона его подделать нельзя. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Prediction() {
    }

    public Prediction(FriendGroup group, AppUser user, FootballMatch match,
                      int homeGoals, int awayGoals, Instant createdAt) {
        this.group = group;
        this.user = user;
        this.match = match;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public FriendGroup getGroup() { return group; }
    public AppUser getUser() { return user; }
    public FootballMatch getMatch() { return match; }
    public int getHomeGoals() { return homeGoals; }
    public int getAwayGoals() { return awayGoals; }
    public Instant getCreatedAt() { return createdAt; }
}
