package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "matches")
public class FootballMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private Long externalId;

    @Column(name = "home_team", nullable = false, length = 100)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 100)
    private String awayTeam;

    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    /** Понедельник недели, в которую проходит матч (тур). */
    @Column(name = "round_week", nullable = false)
    private LocalDate roundWeek;

    @Column(length = 40)
    private String stage;

    private Integer matchday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.SCHEDULED;

    /** Счёт основного времени (90 минут). */
    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    /** Голы только в добавочное время (null: добавочного времени не было). */
    @Column(name = "extra_home")
    private Integer extraHome;

    @Column(name = "extra_away")
    private Integer extraAway;

    /** Голы только в серии пенальти (null: серии не было). */
    @Column(name = "pen_home")
    private Integer penHome;

    @Column(name = "pen_away")
    private Integer penAway;

    /** REGULAR, EXTRA_TIME или PENALTY_SHOOTOUT (null у старых записей: считаем REGULAR). */
    @Column(length = 20)
    private String duration;

    protected FootballMatch() {
    }

    public FootballMatch(Long externalId) {
        this.externalId = externalId;
    }

    /**
     * Обновляет матч данными из football-data.org.
     * Счёт сохраняется только для сыгранного матча, иначе очищается.
     */
    public void applyFeed(String homeTeam, String awayTeam, Instant kickoffAt, LocalDate roundWeek,
                          String stage, Integer matchday, MatchStatus status,
                          Integer homeScore, Integer awayScore,
                          Integer extraHome, Integer extraAway, Integer penHome, Integer penAway,
                          String duration) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoffAt = kickoffAt;
        this.roundWeek = roundWeek;
        this.stage = stage;
        this.matchday = matchday;
        if (status == MatchStatus.FINISHED && homeScore != null && awayScore != null) {
            this.status = MatchStatus.FINISHED;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.extraHome = extraHome;
            this.extraAway = extraAway;
            this.penHome = penHome;
            this.penAway = penAway;
            this.duration = duration;
        } else {
            // "сыгран", но счёта нет: считаем матч ещё идущим, чтобы не подсчитать баллы по пустому счёту
            this.status = status == MatchStatus.FINISHED ? MatchStatus.LIVE : status;
            this.homeScore = null;
            this.awayScore = null;
            this.extraHome = null;
            this.extraAway = null;
            this.penHome = null;
            this.penAway = null;
            this.duration = null;
        }
    }

    public boolean isFinished() {
        return status == MatchStatus.FINISHED && homeScore != null && awayScore != null;
    }

    /** Были ли в матче добавочное время и пенальти (по данным football-data.org). */
    public boolean hadExtraTime() {
        return "EXTRA_TIME".equals(duration) || "PENALTY_SHOOTOUT".equals(duration);
    }

    public boolean hadPenalties() {
        return "PENALTY_SHOOTOUT".equals(duration);
    }

    /**
     * Каких данных не хватает, чтобы посчитать матч по правилам группы: "EXTRA_TIME" и/или "PENALTIES".
     * Пусто, если матч не сыгран или данных достаточно.
     */
    public java.util.List<String> missingParts(ScoreBasis basis) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (!isFinished()) {
            return missing;
        }
        if (basis.extraTime() && hadExtraTime() && extraHome == null) {
            missing.add("EXTRA_TIME");
        }
        if (basis.penalties() && hadPenalties() && penHome == null) {
            missing.add("PENALTIES");
        }
        return missing;
    }

    /** Голы хозяев по выбранному в группе счёту (null, пока матч не сыгран). */
    public Integer scoreHome(ScoreBasis basis) {
        if (homeScore == null) {
            return null;
        }
        int total = homeScore;
        if (basis.extraTime() && extraHome != null) {
            total += extraHome;
        }
        if (basis.penalties() && penHome != null) {
            total += penHome;
        }
        return total;
    }

    /** Голы гостей по выбранному в группе счёту (null, пока матч не сыгран). */
    public Integer scoreAway(ScoreBasis basis) {
        if (awayScore == null) {
            return null;
        }
        int total = awayScore;
        if (basis.extraTime() && extraAway != null) {
            total += extraAway;
        }
        if (basis.penalties() && penAway != null) {
            total += penAway;
        }
        return total;
    }

    public Long getId() { return id; }
    public Long getExternalId() { return externalId; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public Instant getKickoffAt() { return kickoffAt; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public String getStage() { return stage; }
    public Integer getMatchday() { return matchday; }
    public MatchStatus getStatus() { return status; }
    public Integer getHomeScore() { return homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public Integer getExtraHome() { return extraHome; }
    public Integer getExtraAway() { return extraAway; }
    public Integer getPenHome() { return penHome; }
    public Integer getPenAway() { return penAway; }
}
