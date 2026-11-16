package com.example.predictor.feed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Части ответа api-sports.io (API-Football v3), которые нам нужны. */
public final class ApiFootballDtos {

    private ApiFootballDtos() {
    }

    /** errors приходит либо пустым списком, либо объектом с полями — читаем как строку. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Item> response, Integer results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Fixture fixture, League league, Teams teams, Goals goals, Score score) {
    }

    /** date — время начала в ISO с зоной. status.shortName (short) — состояние матча. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fixture(Long id, String date, Status status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(@com.fasterxml.jackson.annotation.JsonProperty("short") String code,
                         Integer elapsed) {
    }

    /** round — «Group Stage - 5» или «Regular Season - 14». */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(Integer id, String name, String round) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Teams(Team home, Team away) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Integer id, String name, String logo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(Integer home, Integer away) {
    }

    /** fulltime — счёт после 90 минут, extratime и penalty — добавочное и серия. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(Goals halftime, Goals fulltime, Goals extratime, Goals penalty) {
    }
}
