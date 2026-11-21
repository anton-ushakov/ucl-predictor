package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Личные встречи двух команд перед матчем: кто кого сколько раз обыгрывал. */
@Entity
@Table(name = "match_head2head")
public class MatchHeadToHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "home_wins", nullable = false)
    private int homeWins;

    @Column(name = "draws", nullable = false)
    private int draws;

    @Column(name = "away_wins", nullable = false)
    private int awayWins;

    @Column(name = "total_goals", nullable = false)
    private int totalGoals;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchHeadToHead() {
    }

    public MatchHeadToHead(Long matchId, int total, int homeWins, int draws, int awayWins,
                           int totalGoals, Instant updatedAt) {
        this.matchId = matchId;
        this.total = total;
        this.homeWins = homeWins;
        this.draws = draws;
        this.awayWins = awayWins;
        this.totalGoals = totalGoals;
        this.updatedAt = updatedAt;
    }

    public Long getMatchId() {
        return matchId;
    }

    public int getTotal() {
        return total;
    }

    public int getHomeWins() {
        return homeWins;
    }

    public int getDraws() {
        return draws;
    }

    public int getAwayWins() {
        return awayWins;
    }

    public int getTotalGoals() {
        return totalGoals;
    }
}
