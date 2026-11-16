package com.example.predictor.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Клиент api-sports.io (API-Football v3). Нужен ради турниров, которых нет в бесплатном
 * тарифе football-data.org: Лига Европы, чемпионат России, Лига наций.
 *
 * <p>Бесплатный тариф даёт 100 запросов в сутки на всё, поэтому клиент сам считает
 * израсходованное и отказывается выходить за отведённый предел. Лучше не показать свежий счёт,
 * чем остаться без источника до завтра.
 *
 * <p>Ключ берётся из переменной окружения API_FOOTBALL_KEY и в логи не попадает.
 */
@Component
public class ApiFootballClient {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballClient.class);

    /** Дневной лимит исчерпан: до завтра не спрашиваем. */
    public static class BudgetException extends RuntimeException {
        public BudgetException(int used, int limit) {
            super("Истрачен дневной запас запросов к api-sports.io (" + used + " из " + limit + ")");
        }
    }

    private final RestClient http;
    private final boolean configured;
    private final int dailyLimit;

    /** Счётчик запросов за сутки. Обнуляется при смене даты по UTC, как и лимит у источника. */
    private LocalDate day = LocalDate.now(ZoneOffset.UTC);
    private int used = 0;

    public ApiFootballClient(
            @Value("${app.api-football.base-url:https://v3.football.api-sports.io}") String baseUrl,
            @Value("${app.api-football.key:${API_FOOTBALL_KEY:}}") String key,
            @Value("${app.api-football.daily-limit:90}") int dailyLimit) {
        this.configured = key != null && !key.isBlank();
        this.dailyLimit = dailyLimit;
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (configured) {
            builder.defaultHeader("x-apisports-key", key.trim());
        }
        this.http = builder.build();
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Сколько запросов ещё можно потратить сегодня. */
    public synchronized int left() {
        rollOver();
        return Math.max(0, dailyLimit - used);
    }

    /** Матчи турнира за промежуток дат: один запрос. */
    public ApiFootballDtos.Response fetchFixtures(int leagueId, int season, LocalDate from,
                                                  LocalDate to) {
        spend();
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("api-sports.io ответил "
                            + response.getStatusCode().value() + " на запрос турнира " + leagueId);
                })
                .body(ApiFootballDtos.Response.class);
    }

    /**
     * Все матчи, идущие прямо сейчас, одним запросом. leagues — номера турниров через дефис
     * («3-235») или «all». Нам хватает одного запроса на все наши турниры сразу.
     */
    public ApiFootballDtos.Response fetchLive(String leagues) {
        spend();
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures")
                        .queryParam("live", leagues)
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("api-sports.io ответил "
                            + response.getStatusCode().value() + " на запрос идущих матчей");
                })
                .body(ApiFootballDtos.Response.class);
    }

    /** Отмечает израсходованный запрос и не даёт выйти за дневной предел. */
    private synchronized void spend() {
        rollOver();
        if (used >= dailyLimit) {
            throw new BudgetException(used, dailyLimit);
        }
        used++;
        if (used == dailyLimit) {
            log.warn("Дневной запас запросов к api-sports.io израсходован, продолжим завтра");
        }
    }

    private void rollOver() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
    }

    /** Время начала матча из ответа источника. */
    static Instant parseDate(String value) {
        try {
            return java.time.OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
