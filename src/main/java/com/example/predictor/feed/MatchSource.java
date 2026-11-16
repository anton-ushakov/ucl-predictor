package com.example.predictor.feed;

/**
 * Откуда приезжают матчи. Источников два: football-data.org для главных турниров
 * и api-sports.io для тех, которых там нет (Лига Европы, РПЛ, Лига наций).
 * У матчей, добавленных владельцем группы руками, источника нет.
 */
public final class MatchSource {

    public static final String FOOTBALL_DATA = "FOOTBALL_DATA";
    public static final String API_FOOTBALL = "API_FOOTBALL";
    /** Турнир заполняется руками. */
    public static final String MANUAL = "MANUAL";

    private MatchSource() {
    }
}
