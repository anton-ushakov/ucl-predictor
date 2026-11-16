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
    private final RoundFinalizer finalizer;
    private final NotificationGenerator notifications;
    private boolean warnedAboutToken;

    private final com.example.predictor.round.AutoSelectionService autoSelection;
    private final ApiFootballSyncService apiFootball;

    public ScheduledTasks(FootballDataClient client, MatchSyncService sync, RoundFinalizer finalizer,
                          NotificationGenerator notifications,
                          com.example.predictor.round.AutoSelectionService autoSelection,
                          ApiFootballSyncService apiFootball) {
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

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${app.football-data.sync-interval:PT30M}")
    public void run() {
        if (client.isConfigured()) {
            try {
                MatchSyncService.Result r = sync.sync();
                log.info("Матчи обновлены: турниров {}, получено {}, создано {}, обновлено {}, пропущено {}",
                        r.competitions(), r.fetched(), r.created(), r.updated(), r.skipped());
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
}
