package com.example.predictor.feed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Клиент football-data.org. Токен берётся из переменной окружения FOOTBALL_DATA_TOKEN
 * (или из свойства app.football-data.token) и никогда не пишется в лог и не хранится в проекте.
 */
@Component
public class FootballDataClient {

    private final RestClient http;
    private final String competition;
    private final boolean configured;

    public FootballDataClient(
            @Value("${app.football-data.base-url:https://api.football-data.org/v4}") String baseUrl,
            @Value("${app.football-data.token:${FOOTBALL_DATA_TOKEN:}}") String token,
            @Value("${app.football-data.competition:CL}") String competition) {
        this.competition = competition;
        this.configured = token != null && !token.isBlank();
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (configured) {
            builder.defaultHeader("X-Auth-Token", token.trim());
        }
        this.http = builder.build();
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Все матчи текущего сезона турнира: один запрос, укладывается в лимит бесплатного тарифа. */
    public FeedDtos.Response fetchMatches() {
        return http.get()
                .uri("/competitions/{code}/matches", competition)
                .retrieve()
                .body(FeedDtos.Response.class);
    }
}
