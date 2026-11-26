package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "matches")
public class FootballMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер матча в football-data.org. У матчей, добавленных вручную, его нет. */
    @Column(name = "external_id", unique = true)
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id")
    private Competition competition;

    /**
     * Группа, владелец которой добавил матч вручную. Такой матч виден только этой группе.
     * У матчей из football-data.org null: они общие для всех.
     */
    @Column(name = "owner_group_id")
    private Long ownerGroupId;

    @Column(name = "created_by")
    private Long createdBy;

    /** Адреса картинок с эмблемами команд. Приходят из источника, у ручных матчей их нет. */
    @Column(name = "home_crest", length = 300)
    private String homeCrest;

    @Column(name = "away_crest", length = 300)
    private String awayCrest;

    @Column(name = "home_team", nullable = false, length = 100)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 100)
    private String awayTeam;

    /**
     * Номера команд у источника. По ним матч связывается с таблицей своего турнира:
     * по названиям это ненадёжно — в матче «Man United», в таблице «Manchester United FC».
     */
    @Column(name = "home_team_id")
    private Long homeTeamId;

    @Column(name = "away_team_id")
    private Long awayTeamId;

    /**
     * Когда счёт стал окончательным. От него отсчитывается пауза перед подведением итогов:
     * источник правит счёт и после финального свистка, а ждать «столько-то часов от начала»
     * нельзя — матч с добавочным временем и пенальти идёт дольше трёх часов.
     */
    @Column(name = "settled_at")
    private Instant settledAt;

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

    /** Откуда приехал матч: FOOTBALL_DATA или API_FOOTBALL. У ручных матчей пусто. */
    @Column(length = 20)
    private String source;

    /** Счёт идущего матча. Только для показа: баллы начисляются по сыгранному. */
    @Column(name = "live_home")
    private Integer liveHome;

    @Column(name = "live_away")
    private Integer liveAway;

    protected FootballMatch() {
    }

    public FootballMatch(Long externalId) {
        this(externalId, "FOOTBALL_DATA");
    }

    public FootballMatch(Long externalId, String source) {
        this.source = source;
        this.externalId = externalId;
    }

    /** Матч, добавленный владельцем группы вручную. */
    public FootballMatch(Competition competition, Long ownerGroupId, Long createdBy,
                         String homeTeam, String awayTeam, Instant kickoffAt, LocalDate roundWeek,
                         String stage) {
        this.competition = competition;
        this.ownerGroupId = ownerGroupId;
        this.createdBy = createdBy;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        // источник иногда отдаёт матч без эмблемы: прежнюю в этом случае не теряем
        if (homeCrest != null) {
            this.homeCrest = homeCrest;
        }
        if (awayCrest != null) {
            this.awayCrest = awayCrest;
        }
        this.kickoffAt = kickoffAt;
        this.roundWeek = roundWeek;
        this.stage = stage;
        this.status = MatchStatus.SCHEDULED;
    }

    /** Эмблемы ручного матча: берутся из справочника команд. */
    public void setCrests(String homeCrest, String awayCrest) {
        this.homeCrest = homeCrest;
        this.awayCrest = awayCrest;
    }

    /** Матч добавлен вручную: счёт вводит владелец группы, из источника он не придёт. */
    public boolean isManual() {
        return externalId == null;
    }

    public void setCompetition(Competition competition) {
        this.competition = competition;
    }

    /** Правит у ручного матча то, что владелец мог указать с ошибкой. */
    public void editManual(String homeTeam, String awayTeam, Instant kickoffAt, LocalDate roundWeek,
                           String stage) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        // источник иногда отдаёт матч без эмблемы: прежнюю в этом случае не теряем
        if (homeCrest != null) {
            this.homeCrest = homeCrest;
        }
        if (awayCrest != null) {
            this.awayCrest = awayCrest;
        }
        this.kickoffAt = kickoffAt;
        this.roundWeek = roundWeek;
        this.stage = stage;
    }

    /**
     * Владелец вводит счёт ручного матча. Добавочное время и пенальти задаются отдельно
     * (голы только этого периода), null — такого периода не было.
     */
    public void setManualScore(Integer homeScore, Integer awayScore,
                               Integer extraHome, Integer extraAway,
                               Integer penHome, Integer penAway) {
        if (homeScore == null || awayScore == null) {
            this.status = MatchStatus.SCHEDULED;
            this.homeScore = null;
            this.awayScore = null;
            this.extraHome = null;
            this.extraAway = null;
            this.penHome = null;
            this.penAway = null;
            this.duration = null;
            return;
        }
        this.status = MatchStatus.FINISHED;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.extraHome = extraHome;
        this.extraAway = extraAway;
        this.penHome = penHome;
        this.penAway = penAway;
        if (penHome != null || penAway != null) {
            this.duration = "PENALTY_SHOOTOUT";
        } else if (extraHome != null || extraAway != null) {
            this.duration = "EXTRA_TIME";
        } else {
            this.duration = "REGULAR";
        }
    }

    /** Владелец отменил ручной матч: он перестаёт учитываться в итогах. */
    public void cancelManual() {
        this.status = MatchStatus.CANCELLED;
    }

    /**
     * Обновляет матч данными из football-data.org.
     * Счёт сохраняется только для сыгранного матча, иначе очищается.
     *
     * @return true, если у уже сыгранного матча поменялся счёт. Источник иногда правит его
     *         задним числом — например, отменяет гол или уточняет автогол, — и тогда итоги
     *         туров с этим матчем надо пересчитать: иначе в таблице останутся чужие баллы.
     */
    public boolean applyFeed(String homeTeam, String awayTeam, String homeCrest, String awayCrest,
                             Instant kickoffAt, LocalDate roundWeek,
                             String stage, Integer matchday, MatchStatus status,
                             Integer homeScore, Integer awayScore,
                             Integer extraHome, Integer extraAway, Integer penHome, Integer penAway,
                             String duration) {
        boolean wasFinished = this.status == MatchStatus.FINISHED;
        Integer oldHome = this.homeScore;
        Integer oldAway = this.awayScore;
        Integer oldExtraHome = this.extraHome;
        Integer oldExtraAway = this.extraAway;
        Integer oldPenHome = this.penHome;
        Integer oldPenAway = this.penAway;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        // источник иногда отдаёт матч без эмблемы: прежнюю в этом случае не теряем
        if (homeCrest != null) {
            this.homeCrest = homeCrest;
        }
        if (awayCrest != null) {
            this.awayCrest = awayCrest;
        }
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
            this.liveHome = null;
            this.liveAway = null;
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
            // идущий матч: счёт показываем, но в подсчёт баллов он не идёт
            boolean playing = this.status == MatchStatus.LIVE;
            this.liveHome = playing ? homeScore : null;
            this.liveAway = playing ? awayScore : null;
        }
        return wasFinished && !(Objects.equals(oldHome, this.homeScore)
                && Objects.equals(oldAway, this.awayScore)
                && Objects.equals(oldExtraHome, this.extraHome)
                && Objects.equals(oldExtraAway, this.extraAway)
                && Objects.equals(oldPenHome, this.penHome)
                && Objects.equals(oldPenAway, this.penAway));
    }

    /** Текущий счёт идущего матча: только для показа, баллы по нему не считаются. */
    public String getSource() { return source; }
    public Integer getLiveHome() { return liveHome; }
    public Integer getLiveAway() { return liveAway; }

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
    public Instant getSettledAt() {
        return settledAt;
    }

    /**
     * Отмечает, что счёт этого матча больше не меняется. Любая правка счёта запускает
     * паузу заново: уточнённый счёт — такой же повод подождать, как и первый.
     *
     * @param scoreChanged источник поправил уже записанный счёт
     */
    public void noteSettled(Instant now, boolean scoreChanged) {
        if (status != MatchStatus.FINISHED || homeScore == null) {
            settledAt = null;
            return;
        }
        if (settledAt == null || scoreChanged) {
            settledAt = now;
        }
    }

    public Long getHomeTeamId() {
        return homeTeamId;
    }

    public Long getAwayTeamId() {
        return awayTeamId;
    }

    /** Номера команд из источника: отдельно от applyFeed, потому что приходят не всегда. */
    public void applyTeamIds(Long home, Long away) {
        if (home != null) {
            this.homeTeamId = home;
        }
        if (away != null) {
            this.awayTeamId = away;
        }
    }

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
    public Competition getCompetition() { return competition; }
    public String getHomeCrest() { return homeCrest; }
    public String getAwayCrest() { return awayCrest; }
    public Long getOwnerGroupId() { return ownerGroupId; }
    public Long getCreatedBy() { return createdBy; }
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
