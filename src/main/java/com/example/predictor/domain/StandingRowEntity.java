package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Строка турнирной таблицы, как её отдаёт источник.
 *
 * Своих итогов тут нет — это таблица самого турнира, а не группы. Храним её у себя, чтобы
 * не дёргать источник на каждый просмотр: бесплатный ключ позволяет десять запросов в минуту.
 */
@Entity
@Table(name = "competition_standings")
public class StandingRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    /** Группа, если турнир с группами. Пусто — общая таблица. */
    @Column(name = "group_label", length = 60)
    private String groupLabel;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "team_external_id")
    private Long teamExternalId;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Column(name = "crest", length = 300)
    private String crest;

    @Column(name = "played", nullable = false)
    private int played;

    @Column(name = "won", nullable = false)
    private int won;

    @Column(name = "draw", nullable = false)
    private int draw;

    @Column(name = "lost", nullable = false)
    private int lost;

    @Column(name = "goals_for", nullable = false)
    private int goalsFor;

    @Column(name = "goals_against", nullable = false)
    private int goalsAgainst;

    @Column(name = "points", nullable = false)
    private int points;

    /** Последние матчи в виде «W,W,D,L,W»: источник присылает это вместе с таблицей. */
    @Column(name = "form", length = 40)
    private String form;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StandingRowEntity() {
    }

    public StandingRowEntity(Long competitionId, String groupLabel, int position,
                             Long teamExternalId, String teamName, String crest, int played,
                             int won, int draw, int lost, int goalsFor, int goalsAgainst,
                             int points, String form, Instant updatedAt) {
        this.competitionId = competitionId;
        this.groupLabel = groupLabel;
        this.position = position;
        this.teamExternalId = teamExternalId;
        this.teamName = teamName;
        this.crest = crest;
        this.played = played;
        this.won = won;
        this.draw = draw;
        this.lost = lost;
        this.goalsFor = goalsFor;
        this.goalsAgainst = goalsAgainst;
        this.points = points;
        this.form = form;
        this.updatedAt = updatedAt;
    }

    public Long getCompetitionId() {
        return competitionId;
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public int getPosition() {
        return position;
    }

    public Long getTeamExternalId() {
        return teamExternalId;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getCrest() {
        return crest;
    }

    public int getPlayed() {
        return played;
    }

    public int getWon() {
        return won;
    }

    public int getDraw() {
        return draw;
    }

    public int getLost() {
        return lost;
    }

    public int getGoalsFor() {
        return goalsFor;
    }

    public int getGoalsAgainst() {
        return goalsAgainst;
    }

    public int getPoints() {
        return points;
    }

    public String getForm() {
        return form;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
