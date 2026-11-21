package com.example.predictor.feed;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Части ответа football-data.org (API v4), которые нам нужны. Остальные поля игнорируются. */
public final class FeedDtos {

    private FeedDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Match> matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(Long id, String utcDate, String status, String stage, Integer matchday,
                        Team homeTeam, Team awayTeam, Score score) {
    }

    /** crest — адрес картинки с эмблемой клуба или флагом сборной. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(String name, String shortName, String crest) {
    }

    /**
     * duration: REGULAR, EXTRA_TIME или PENALTY_SHOOTOUT.
     * regularTime: счёт к 90-й минуте, extraTime: голы только в добавочное время,
     * penalties: голы только в серии пенальти.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(String duration, Goals fullTime, Goals regularTime, Goals extraTime, Goals penalties) {
    }

    /** В разных версиях документации поля называются home/away или homeTeam/awayTeam. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(@JsonAlias("homeTeam") Integer home, @JsonAlias("awayTeam") Integer away) {
    }

    // ------------------------------------------------------------- турнирная таблица

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsResponse(List<StandingsBlock> standings) {
    }

    /**
     * type: TOTAL, HOME или AWAY — нам нужен только TOTAL. group: название группы,
     * если турнир групповой.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsBlock(String stage, String type, String group, List<TableRow> table) {
    }

    /** form: строка вида «W,W,D,L,W» — последние матчи команды. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableRow(Integer position, TableTeam team, Integer playedGames, Integer won,
                           Integer draw, Integer lost, Integer points, Integer goalsFor,
                           Integer goalsAgainst, String form) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableTeam(Long id, String name, String shortName, String crest) {
    }

    // ------------------------------------------------------------- личные встречи

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadToHeadResponse(Aggregates aggregates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aggregates(Integer numberOfMatches, Integer totalGoals,
                             SideStats homeTeam, SideStats awayTeam) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SideStats(Long id, String name, Integer wins, Integer draws, Integer losses) {
    }
}
