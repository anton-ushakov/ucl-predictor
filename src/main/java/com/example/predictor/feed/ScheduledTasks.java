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

    public ScheduledTasks(FootballDataClient client, MatchSyncService sync, RoundFinalizer finalizer,
                          NotificationGenerator notifications) {
        this.notifications = notifications;
        this.client = client;
        this.sync = sync;
        this.finalizer = finalizer;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${app.football-data.sync-interval:PT30M}")
    public void run() {
        if (client.isConfigured()) {
            try {
                MatchSyncService.Result r = sync.sync();
                log.info("Матчи обновлены: получено {}, создано {}, обновлено {}, пропущено {}",
                        r.fetched(), r.created(), r.updated(), r.skipped());
            } catch (RuntimeException e) {
                log.warn("Не удалось обновить матчи из football-data.org: {}", e.getMessage());
            }
        } else if (!warnedAboutToken) {
            warnedAboutToken = true;
            log.warn("Токен football-data.org не задан (переменная окружения FOOTBALL_DATA_TOKEN): "
                    + "матчи не загружаются");
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
