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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обновляет матчи в нашей базе по данным football-data.org.
 *
 * Турниров несколько, а бесплатный тариф разрешает 10 запросов в минуту, поэтому турниры
 * опрашиваются по одному с паузой между запросами. Каждый турнир сохраняется в своей транзакции:
 * сбой в одном не отменяет остальные.
 */
@Service
public class MatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncService.class);

    /** Сколько недель назад ещё обновляем матчи (счёт мог появиться позже). */
    private static final int WEEKS_BACK = 3;

    public record Result(int competitions, int fetched, int created, int updated, int skipped) {

        Result plus(int fetched, int created, int updated, int skipped) {
            return new Result(this.competitions + 1, this.fetched + fetched, this.created + created,
                    this.updated + updated, this.skipped + skipped);
        }
    }

    private final FootballDataClient client;
    private final MatchRepository matches;
    private final CompetitionRepository competitions;
    private final AppTime time;
    private final TransactionTemplate tx;
    private final Duration pause;

    /** Турниры, недоступные на нашем тарифе: до перезапуска их не спрашиваем. */
    private final Set<String> unavailable = ConcurrentHashMap.newKeySet();

    public MatchSyncService(FootballDataClient client, MatchRepository matches,
                            CompetitionRepository competitions, AppTime time,
                            PlatformTransactionManager transactionManager,
                            @Value("${app.football-data.request-pause:PT7S}") Duration pause) {
        this.client = client;
        this.matches = matches;
        this.competitions = competitions;
        this.time = time;
        this.tx = new TransactionTemplate(transactionManager);
        this.pause = pause;
    }

    /** Обновляет матчи всех включённых турниров, которые загружаются автоматически. */
    public Result sync() {
        LocalDate from = time.currentWeek().minusWeeks(WEEKS_BACK);
        LocalDate to = time.lastPredictableWeek().plusDays(6);
        Result total = new Result(0, 0, 0, 0, 0);

        List<Competition> list = competitions.findBySyncedTrueAndEnabledTrueOrderBySortOrderAsc();
        boolean first = true;
        for (Competition competition : list) {
            String code = competition.getCode();
            if (code == null || code.isBlank() || unavailable.contains(code)) {
                continue;
            }
            if (!first) {
                sleep();
            }
            first = false;
            try {
                Result one = syncCompetition(competition, from, to);
                total = total.plus(one.fetched(), one.created(), one.updated(), one.skipped());
                log.debug("{}: получено {}, создано {}, обновлено {}, пропущено {}",
                        code, one.fetched(), one.created(), one.updated(), one.skipped());
            } catch (FootballDataClient.ForbiddenException e) {
                unavailable.add(code);
                log.warn("{}: турнир недоступен на текущем тарифе, больше не запрашиваем", code);
            } catch (FootballDataClient.RateLimitException e) {
                log.warn("Лимит запросов к football-data.org исчерпан, остальные турниры обновим позже");
                break;
            } catch (RuntimeException e) {
                log.warn("{}: не удалось обновить матчи: {}", code, e.getMessage());
            }
        }
        return total;
    }

    private Result syncCompetition(Competition competition, LocalDate from, LocalDate to) {
        FeedDtos.Response response = client.fetchMatches(competition.getCode(), from, to);
        List<FeedDtos.Match> feed = response == null || response.matches() == null
                ? List.of() : response.matches();

        Map<Long, FeedMapper.Mapped> mapped = new LinkedHashMap<>();
        for (FeedDtos.Match m : feed) {
            FeedMapper.map(m, time.zone()).ifPresent(x -> mapped.put(x.externalId(), x));
        }
        if (mapped.isEmpty()) {
            return new Result(1, feed.size(), 0, 0, 0);
        }

        LocalDate windowFrom = time.currentWeek().minusWeeks(WEEKS_BACK);
        LocalDate windowTo = time.lastPredictableWeek();
        Long competitionId = competition.getId();

        int[] counts = tx.execute(status -> {
            Map<Long, FootballMatch> existing = new HashMap<>();
            for (FootballMatch m : matches.findByExternalIdIn(new ArrayList<>(mapped.keySet()))) {
                existing.put(m.getExternalId(), m);
            }
            Competition ref = competitions.getReferenceById(competitionId);
            int created = 0;
            int updated = 0;
            int skipped = 0;
            for (FeedMapper.Mapped m : mapped.values()) {
                FootballMatch match = existing.get(m.externalId());
                if (match == null) {
                    // новые матчи берём только из рабочего окна: ближайшие недели
                    if (m.roundWeek().isBefore(windowFrom) || m.roundWeek().isAfter(windowTo)) {
                        skipped++;
                        continue;
                    }
                    match = new FootballMatch(m.externalId());
                    match.setCompetition(ref);
                    created++;
                } else {
                    if (match.getCompetition() == null) {
                        match.setCompetition(ref);
                    }
                    updated++;
                }
                FeedMapper.Parts s = m.score();
                match.applyFeed(m.homeTeam(), m.awayTeam(), m.kickoffAt(), m.roundWeek(), m.stage(),
                        m.matchday(), m.status(),
                        s == null ? null : s.homeScore(), s == null ? null : s.awayScore(),
                        s == null ? null : s.extraHome(), s == null ? null : s.extraAway(),
                        s == null ? null : s.penHome(), s == null ? null : s.penAway(),
                        s == null ? null : s.duration());
                matches.save(match);
            }
            return new int[]{created, updated, skipped};
        });
        int[] c = counts == null ? new int[]{0, 0, 0} : counts;
        return new Result(1, feed.size(), c[0], c[1], c[2]);
    }

    private void sleep() {
        if (pause.isZero() || pause.isNegative()) {
            return;
        }
        try {
            Thread.sleep(pause.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
