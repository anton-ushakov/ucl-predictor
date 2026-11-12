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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(String name, String shortName) {
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
}
