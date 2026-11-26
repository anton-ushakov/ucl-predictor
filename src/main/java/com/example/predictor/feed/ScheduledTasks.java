package com.example.predictor.feed;

import com.example.predictor.notify.NotificationGenerator;
import com.example.predictor.round.RoundFinalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Фоновая работа: обновить матчи из football-data.org и подвести итоги завершённых туров.
 * Запускается через 20 секунд после старта приложения, дальше с интервалом app.football-data.sync-interval
 * (по умолчанию 30 минут; формат вроде PT30M).
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final FootballDataClient client;
    private final MatchSyncService sync;
    private final ExtraDataSyncService extra;
    private final RoundFinalizer finalizer;
    private final NotificationGenerator notifications;
    private boolean warnedAboutToken;

    private final com.example.predictor.round.AutoSelectionService autoSelection;
    private final ApiFootballSyncService apiFootball;
    private final com.example.predictor.round.RoundRecountService recount;

    public ScheduledTasks(FootballDataClient client, MatchSyncService sync, RoundFinalizer finalizer,
                          NotificationGenerator notifications,
                          com.example.predictor.round.AutoSelectionService autoSelection,
                          ApiFootballSyncService apiFootball,
                          ExtraDataSyncService extra,
                          com.example.predictor.round.RoundRecountService recount) {
        this.recount = recount;
        this.extra = extra;
        this.apiFootball = apiFootball;
        this.autoSelection = autoSelection;
        this.notifications = notifications;
        this.client = client;
        this.sync = sync;
        this.finalizer = finalizer;
    }

    /**
     * Счёт идущих матчей из api-sports.io. Отдельно от общего обновления: пока матчи идут,
     * смотреть раз в полчаса поздно, а когда их нет, запрос не тратится совсем.
     */
    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "${app.api-football.live-interval:PT10M}")
    public void liveScores() {
        if (!apiFootball.isConfigured()) {
            return;
        }
        try {
            int touched = apiFootball.syncLive();
            if (touched > 0) {
                log.debug("Счёт идущих матчей обновлён: {}", touched);
            }
        } catch (RuntimeException e) {
            log.warn("Не удалось обновить счёт идущих матчей: {}", e.getMessage());
        }
    }

    /**
     * Счёт матчей, которые идут прямо сейчас, из football-data.org.
     *
     * Общее обновление ходит раз в полчаса, и для игрового вечера это слишком редко: матч
     * кончился, а счёта в приложении ещё двадцать минут нет. Здесь спрашиваются только
     * турниры с идущими матчами и только пара дней вокруг сегодняшнего, поэтому запросов
     * уходит немного, а в тихие дни не уходит ни одного.
     *
     * Сразу следом подводятся итоги и готовятся оповещения: иначе счёт уже обновился, а
     * «итог тура» пришёл бы через полчаса.
     */
    @Scheduled(initialDelayString = "PT90S", fixedDelayString = "${app.football-data.live-interval:PT5M}")
    public void playing() {
        if (!client.isConfigured()) {
            return;
        }
        try {
            MatchSyncService.Result r = sync.syncPlaying();
            if (r.competitions() == 0) {
                return;
            }
            log.info("Идущие матчи обновлены: турниров {}, получено {}, обновлено {}",
                    r.competitions(), r.fetched(), r.updated());
            recountChanged(r);
        } catch (RuntimeException e) {
            log.warn("Не удалось обновить счёт идущих матчей: {}", e.getMessage());
            return;
        }
        try {
            finalizer.finalizeReadyRounds();
        } catch (RuntimeException e) {
            log.error("Ошибка при подведении итогов туров", e);
        }
        try {
            notifications.generate();
        } catch (RuntimeException e) {
            log.error("Ошибка при подготовке оповещений", e);
        }
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${app.football-data.sync-interval:PT30M}")
    public void run() {
        if (client.isConfigured()) {
            try {
                MatchSyncService.Result r = sync.sync();
                log.info("Матчи обновлены: турниров {}, получено {}, создано {}, обновлено {}, пропущено {}",
                        r.competitions(), r.fetched(), r.created(), r.updated(), r.skipped());
                recountChanged(r);
            } catch (RuntimeException e) {
                log.warn("Не удалось обновить матчи из football-data.org: {}", e.getMessage());
            }
        } else if (!warnedAboutToken) {
            warnedAboutToken = true;
            log.warn("Токен football-data.org не задан (переменная окружения FOOTBALL_DATA_TOKEN): "
                    + "матчи не загружаются");
        }
        // второй источник: турниры, которых нет в бесплатном тарифе football-data.org
        if (apiFootball.isConfigured()) {
            try {
                ApiFootballSyncService.Result r = apiFootball.syncFixtures(false);
                if (r.competitions() > 0) {
                    log.info("api-sports.io: турниров {}, получено {}, создано {}, обновлено {}, "
                                    + "пропущено {}; запросов осталось сегодня {}",
                            r.competitions(), r.fetched(), r.created(), r.updated(), r.skipped(),
                            apiFootball.requestsLeft());
                }
            } catch (RuntimeException e) {
                log.warn("Не удалось обновить матчи из api-sports.io: {}", e.getMessage());
            }
        }
        try {
            autoSelection.run();
        } catch (RuntimeException e) {
            log.error("Ошибка автосостава туров", e);
        }
        try {
            finalizer.finalizeReadyRounds();
        } catch (RuntimeException e) {
            log.error("Ошибка при подведении итогов туров", e);
        }
        try {
            notifications.generate();
        } catch (RuntimeException e) {
            log.error("Ошибка при подготовке оповещений", e);
        }
    }

    /**
     * Источник поправил счёт уже сыгранного матча — значит, подведённые по нему итоги надо
     * пересчитать. Иначе в таблице тура навсегда останутся чужие баллы.
     */
    private void recountChanged(MatchSyncService.Result r) {
        if (r.changed().isEmpty()) {
            return;
        }
        try {
            int rounds = recount.recountRoundsOf(r.changed());
            if (rounds > 0) {
                log.info("Счёт поправлен у матчей {} — пересчитаны итоги туров: {}",
                        r.changed(), rounds);
            }
        } catch (RuntimeException e) {
            log.error("Ошибка пересчёта итогов после правки счёта", e);
        }
    }

    /**
     * Турнирные таблицы и личные встречи. Раз в час: таблица меняется после матчей, а не
     * каждую минуту, и лимит запросов тратить на неё чаще незачем.
     */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${app.football-data.extra-interval:PT1H}")
    public void extras() {
        try {
            int tables = extra.syncStandings();
            int pairs = extra.syncHeadToHead();
            if (tables > 0 || pairs > 0) {
                log.info("Справки обновлены: таблиц {}, личных встреч {}", tables, pairs);
            }
        } catch (RuntimeException e) {
            log.warn("Не удалось обновить справки: {}", e.getMessage());
        }
    }
}
