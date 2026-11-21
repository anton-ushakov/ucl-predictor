package com.example.predictor.feed;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.Competition;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.MatchHeadToHead;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.StandingRowEntity;
import com.example.predictor.repository.CompetitionRepository;
import com.example.predictor.repository.HeadToHeadRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.StandingRowRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Всё, что источник знает о матчах кроме счёта: турнирная таблица и личные встречи.
 *
 * Бесплатный ключ позволяет десять запросов в минуту, поэтому берём редко и храним у себя:
 * таблицы — раз в час, по одному запросу на турнир; личные встречи — понемногу и только для
 * матчей, которые скоро сыграют, да и то один раз на матч.
 *
 * Если источник отвечает отказом — такое бывает на бесплатном тарифе, — запоминаем это и
 * больше не спрашиваем до перезапуска: экран просто обойдётся без этой строки.
 */
@Service
public class ExtraDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExtraDataSyncService.class);

    /** Сколько матчей за раз спрашиваем про личные встречи: больше не позволит лимит. */
    private static final int HEAD_TO_HEAD_PER_RUN = 5;

    /** Сколько прошлых встреч учитываем. */
    private static final int HEAD_TO_HEAD_DEPTH = 10;

    private final FootballDataClient client;
    private final CompetitionRepository competitions;
    private final StandingRowRepository standings;
    private final HeadToHeadRepository headToHead;
    private final MatchRepository matches;
    private final AppTime time;
    private final TransactionTemplate tx;
    private final Duration pause;

    /** Турниры и возможности, закрытые на нашем тарифе: до перезапуска не спрашиваем. */
    private final Set<String> unavailable = ConcurrentHashMap.newKeySet();

    public ExtraDataSyncService(FootballDataClient client, CompetitionRepository competitions,
                                StandingRowRepository standings, HeadToHeadRepository headToHead,
                                MatchRepository matches, AppTime time,
                                PlatformTransactionManager transactionManager,
                                @Value("${app.football-data.request-pause:PT7S}") Duration pause) {
        this.client = client;
        this.competitions = competitions;
        this.standings = standings;
        this.headToHead = headToHead;
        this.matches = matches;
        this.time = time;
        this.tx = new TransactionTemplate(transactionManager);
        this.pause = pause;
    }

    /** Обновляет таблицы всех турниров, которые загружаются автоматически. */
    public int syncStandings() {
        if (!client.isConfigured()) {
            return 0;
        }
        int done = 0;
        boolean first = true;
        for (Competition competition : competitions
                .findBySourceAndSyncedTrueAndEnabledTrueOrderBySortOrderAsc(
                        MatchSource.FOOTBALL_DATA)) {
            String code = competition.getCode();
            if (code == null || code.isBlank() || unavailable.contains(code)) {
                continue;
            }
            if (!first) {
                sleep();
            }
            first = false;
            try {
                done += saveStandings(competition, client.fetchStandings(code)) ? 1 : 0;
            } catch (FootballDataClient.ForbiddenException e) {
                unavailable.add(code);
                log.warn("{}: таблица недоступна на нашем тарифе, больше не спрашиваем", code);
            } catch (FootballDataClient.RateLimitException e) {
                log.warn("Лимит запросов исчерпан: остальные таблицы обновим позже");
                break;
            } catch (RuntimeException e) {
                log.warn("{}: не удалось обновить таблицу: {}", code, e.getMessage());
            }
        }
        return done;
    }

    private boolean saveStandings(Competition competition, FeedDtos.StandingsResponse response) {
        if (response == null || response.standings() == null) {
            return false;
        }
        Instant now = Instant.now();
        List<StandingRowEntity> rows = new ArrayList<>();
        for (FeedDtos.StandingsBlock block : response.standings()) {
            // HOME и AWAY — это та же таблица, только по домашним и гостевым матчам
            if (block.type() != null && !"TOTAL".equalsIgnoreCase(block.type())) {
                continue;
            }
            if (block.table() == null) {
                continue;
            }
            for (FeedDtos.TableRow row : block.table()) {
                if (row == null || row.team() == null) {
                    continue;
                }
                rows.add(new StandingRowEntity(competition.getId(), block.group(),
                        value(row.position()), row.team().id(), row.team().name(),
                        row.team().crest(), value(row.playedGames()), value(row.won()),
                        value(row.draw()), value(row.lost()), value(row.goalsFor()),
                        value(row.goalsAgainst()), value(row.points()), row.form(), now));
            }
        }
        if (rows.isEmpty()) {
            return false;
        }
        Long competitionId = competition.getId();
        tx.executeWithoutResult(status -> {
            standings.deleteByCompetitionId(competitionId);
            standings.flush();
            standings.saveAll(rows);
        });
        return true;
    }

    /**
     * Берёт личные встречи для ближайших матчей, о которых ещё не спрашивали. По несколько
     * штук за раз: это справка, а не счёт, и подождать она может.
     */
    public int syncHeadToHead() {
        if (!client.isConfigured() || unavailable.contains("head2head")) {
            return 0;
        }
        Instant now = Instant.now();
        List<FootballMatch> soon = matches.findByStatusAndKickoffAtBetweenOrderByKickoffAtAsc(
                MatchStatus.SCHEDULED, now, now.plus(Duration.ofDays(8)));
        Set<Long> known = new HashSet<>();
        if (!soon.isEmpty()) {
            for (MatchHeadToHead h : headToHead.findByMatchIdIn(
                    soon.stream().map(FootballMatch::getId).toList())) {
                known.add(h.getMatchId());
            }
        }

        int done = 0;
        boolean first = true;
        for (FootballMatch match : soon) {
            if (done >= HEAD_TO_HEAD_PER_RUN) {
                break;
            }
            if (match.getExternalId() == null || known.contains(match.getId())) {
                continue;
            }
            if (!first) {
                sleep();
            }
            first = false;
            try {
                FeedDtos.HeadToHeadResponse response =
                        client.fetchHeadToHead(match.getExternalId(), HEAD_TO_HEAD_DEPTH);
                if (save(match, response)) {
                    done++;
                }
            } catch (FootballDataClient.ForbiddenException e) {
                unavailable.add("head2head");
                log.warn("Личные встречи недоступны на нашем тарифе, больше не спрашиваем");
                break;
            } catch (FootballDataClient.RateLimitException e) {
                log.warn("Лимит запросов исчерпан: личные встречи возьмём позже");
                break;
            } catch (RuntimeException e) {
                log.warn("Не удалось взять личные встречи матча {}: {}", match.getId(),
                        e.getMessage());
            }
        }
        return done;
    }

    private boolean save(FootballMatch match, FeedDtos.HeadToHeadResponse response) {
        if (response == null || response.aggregates() == null) {
            return false;
        }
        FeedDtos.Aggregates a = response.aggregates();
        FeedDtos.SideStats home = a.homeTeam();
        FeedDtos.SideStats away = a.awayTeam();
        if (home == null || away == null) {
            return false;
        }
        MatchHeadToHead row = new MatchHeadToHead(match.getId(), value(a.numberOfMatches()),
                value(home.wins()), value(home.draws()), value(away.wins()),
                value(a.totalGoals()), Instant.now());
        tx.executeWithoutResult(status -> headToHead.save(row));
        return true;
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    /** Пауза между запросами: бесплатный ключ не любит частых. */
    private void sleep() {
        try {
            Thread.sleep(pause.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
