package com.example.predictor.feed;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.Competition;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.repository.CompetitionRepository;
import com.example.predictor.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Загрузка матчей из api-sports.io. Турниров там сколько угодно, а запросов в сутки сто,
 * поэтому расписание берём несколько раз в день, а счёт идущих матчей — одним запросом
 * на все турниры сразу и только пока матчи действительно идут.
 */
@Service
public class ApiFootballSyncService {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballSyncService.class);

    /** Сколько недель назад ещё обновляем матчи. */
    private static final int WEEKS_BACK = 3;

    public record Result(int competitions, int fetched, int created, int updated, int skipped) {
    }

    private final ApiFootballClient client;
    private final MatchRepository matches;
    private final CompetitionRepository competitions;
    private final AppTime time;
    private final Duration fixturesInterval;
    private final TransactionTemplate tx;

    private Instant fixturesCheckedAt;

    public ApiFootballSyncService(ApiFootballClient client, MatchRepository matches,
                                  CompetitionRepository competitions, AppTime time,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${app.api-football.fixtures-interval:PT6H}")
                                  Duration fixturesInterval) {
        this.tx = new TransactionTemplate(transactionManager);
        this.client = client;
        this.matches = matches;
        this.competitions = competitions;
        this.time = time;
        this.fixturesInterval = fixturesInterval;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    /**
     * Расписание турниров этого источника. Вызывается часто, а работает редко: между заходами
     * выдерживается app.api-football.fixtures-interval, иначе дневного запаса не хватит.
     */
    public Result syncFixtures(boolean force) {
        if (!client.isConfigured()) {
            return new Result(0, 0, 0, 0, 0);
        }
        Instant now = Instant.now();
        if (!force && fixturesCheckedAt != null
                && fixturesCheckedAt.plus(fixturesInterval).isAfter(now)) {
            return new Result(0, 0, 0, 0, 0);
        }
        fixturesCheckedAt = now;

        LocalDate from = time.currentWeek().minusWeeks(WEEKS_BACK);
        LocalDate to = time.lastPredictableWeek().plusDays(6);
        int season = seasonOf(time.currentWeek());

        int done = 0;
        int fetched = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Competition competition : enabled()) {
            if (competition.getExternalId() == null) {
                continue;
            }
            try {
                ApiFootballDtos.Response response =
                        client.fetchFixtures(competition.getExternalId(), season, from, to);
                List<ApiFootballDtos.Item> items = response == null || response.response() == null
                        ? List.of() : response.response();
                int[] counts = save(competition, items, from, to);
                done++;
                fetched += items.size();
                created += counts[0];
                updated += counts[1];
                skipped += counts[2];
                if (items.isEmpty()) {
                    log.info("{}: источник не вернул ни одного матча. Возможно, у турнира другой "
                            + "номер или другой сезон (запрашивали league={}, season={})",
                            competition.getShortName(), competition.getExternalId(), season);
                }
            } catch (ApiFootballClient.BudgetException e) {
                log.warn("{}", e.getMessage());
                break;
            } catch (RuntimeException e) {
                log.warn("{}: не удалось обновить матчи: {}", competition.getShortName(), e.getMessage());
            }
        }
        return new Result(done, fetched, created, updated, skipped);
    }

    /**
     * Счёт матчей, которые идут прямо сейчас. Один запрос на все турниры сразу,
     * и только если в нашей базе есть матч, который мог начаться.
     */
    public int syncLive() {
        if (!client.isConfigured()) {
            return 0;
        }
        List<Competition> list = enabled();
        if (list.isEmpty() || !somethingIsPlaying(list)) {
            return 0;
        }
        StringBuilder leagues = new StringBuilder();
        for (Competition c : list) {
            if (c.getExternalId() == null) {
                continue;
            }
            if (leagues.length() > 0) {
                leagues.append('-');
            }
            leagues.append(c.getExternalId());
        }
        if (leagues.length() == 0) {
            return 0;
        }
        try {
            ApiFootballDtos.Response response = client.fetchLive(leagues.toString());
            List<ApiFootballDtos.Item> items = response == null || response.response() == null
                    ? List.of() : response.response();
            int touched = 0;
            for (Competition c : list) {
                List<ApiFootballDtos.Item> mine = new ArrayList<>();
                for (ApiFootballDtos.Item item : items) {
                    if (item.league() != null && c.getExternalId().equals(item.league().id())) {
                        mine.add(item);
                    }
                }
                if (!mine.isEmpty()) {
                    int[] counts = save(c, mine, null, null);
                    touched += counts[1];
                }
            }
            return touched;
        } catch (ApiFootballClient.BudgetException e) {
            log.warn("{}", e.getMessage());
            return 0;
        } catch (RuntimeException e) {
            log.warn("Не удалось обновить счёт идущих матчей: {}", e.getMessage());
            return 0;
        }
    }

    /** Есть ли матч, который сейчас может идти: начался не больше трёх часов назад. */
    private boolean somethingIsPlaying(List<Competition> list) {
        Instant now = Instant.now();
        List<Long> ids = new ArrayList<>();
        for (Competition c : list) {
            ids.add(c.getId());
        }
        return matches.countPlaying(ids, now.minus(Duration.ofHours(3)), now) > 0;
    }

    /** Сохраняет матчи турнира. Окно дат пустое — значит обновляем только уже известные матчи. */
    private int[] save(Competition competition, List<ApiFootballDtos.Item> items,
                       LocalDate windowFrom, LocalDate windowTo) {
        Map<Long, FeedMapper.Mapped> mapped = new LinkedHashMap<>();
        for (ApiFootballDtos.Item item : items) {
            ApiFootballMapper.map(item, time.zone()).ifPresent(x -> mapped.put(x.externalId(), x));
        }
        if (mapped.isEmpty()) {
            return new int[]{0, 0, 0};
        }
        int[] counts = tx.execute(status -> saveAll(competition, mapped, windowFrom, windowTo));
        return counts == null ? new int[]{0, 0, 0} : counts;
    }

    private int[] saveAll(Competition competition, Map<Long, FeedMapper.Mapped> mapped,
                          LocalDate windowFrom, LocalDate windowTo) {
        Map<Long, FootballMatch> existing = new HashMap<>();
        for (FootballMatch m : matches.findBySourceAndExternalIdIn(MatchSource.API_FOOTBALL,
                new ArrayList<>(mapped.keySet()))) {
            existing.put(m.getExternalId(), m);
        }
        Competition ref = competitions.getReferenceById(competition.getId());
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (FeedMapper.Mapped m : mapped.values()) {
            FootballMatch match = existing.get(m.externalId());
            if (match == null) {
                if (windowFrom == null || m.roundWeek().isBefore(windowFrom)
                        || m.roundWeek().isAfter(windowTo)) {
                    skipped++;
                    continue;
                }
                match = new FootballMatch(m.externalId(), MatchSource.API_FOOTBALL);
                match.setCompetition(ref);
                created++;
            } else {
                if (match.getCompetition() == null) {
                    match.setCompetition(ref);
                }
                updated++;
            }
            FeedMapper.Parts s = m.score();
            match.applyFeed(m.homeTeam(), m.awayTeam(), m.homeCrest(), m.awayCrest(),
                    m.kickoffAt(), m.roundWeek(), m.stage(), m.matchday(), m.status(),
                    s == null ? null : s.homeScore(), s == null ? null : s.awayScore(),
                    s == null ? null : s.extraHome(), s == null ? null : s.extraAway(),
                    s == null ? null : s.penHome(), s == null ? null : s.penAway(),
                    s == null ? null : s.duration());
            matches.save(match);
        }
        return new int[]{created, updated, skipped};
    }

    private List<Competition> enabled() {
        return competitions.findBySourceAndSyncedTrueAndEnabledTrueOrderBySortOrderAsc(
                MatchSource.API_FOOTBALL);
    }

    /**
     * Сезон в понимании источника — год, в котором он начался: для июля и позже это текущий год.
     * Чемпионаты, которые идут по календарному году, попадают туда же.
     */
    static int seasonOf(LocalDate week) {
        return week.getMonthValue() >= 7 ? week.getYear() : week.getYear() - 1;
    }

    /** Для журнала: сколько запросов ещё можно потратить сегодня. */
    public int requestsLeft() {
        return client.left();
    }

    /** Матчи, которые сейчас идут или вот-вот начнутся (по нашим данным). */
    public long playingNow() {
        Instant now = Instant.now();
        List<Long> ids = new ArrayList<>();
        for (Competition c : enabled()) {
            ids.add(c.getId());
        }
        return ids.isEmpty() ? 0
                : matches.countPlaying(ids, now.minus(Duration.ofHours(3)), now);
    }
}
