package com.example.predictor.feed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * Клиент football-data.org. Токен берётся из переменной окружения FOOTBALL_DATA_TOKEN
 * (или из свойства app.football-data.token) и никогда не пишется в лог и не хранится в проекте.
 */
@Component
public class FootballDataClient {

    /** Турнир недоступен на нашем тарифе: источник ответил 403. */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String code) {
            super("Турнир " + code + " недоступен на текущем тарифе football-data.org");
        }
    }

    /** Превышен лимит запросов (10 в минуту на бесплатном тарифе). */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException() {
            super("Превышен лимит запросов к football-data.org");
        }
    }

    private final RestClient http;
    private final boolean configured;

    public FootballDataClient(
            @Value("${app.football-data.base-url:https://api.football-data.org/v4}") String baseUrl,
            @Value("${app.football-data.token:${FOOTBALL_DATA_TOKEN:}}") String token) {
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

    /**
     * Матчи одного турнира за указанный промежуток дат: один запрос на турнир.
     * Промежуток сужает ответ до нужных нам недель, чтобы не тянуть весь сезон.
     */
    public FeedDtos.Response fetchMatches(String code, LocalDate from, LocalDate to) {
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("dateFrom", from.toString())
                        .queryParam("dateTo", to.toString())
                        .build(code))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 403) {
                        throw new ForbiddenException(code);
                    }
                    if (status == 429) {
                        throw new RateLimitException();
                    }
                    throw new IllegalStateException(
                            "football-data.org ответил " + status + " на запрос турнира " + code);
                })
                .body(FeedDtos.Response.class);
    }
}
