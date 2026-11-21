package com.example.predictor.notify;

import com.example.predictor.common.AdminAccess;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Notification;
import com.example.predictor.domain.NotificationType;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.NotificationRepository;
import com.example.predictor.push.PushSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Оповещения владельцу приложения о справочнике команд.
 *
 * Справочник один на все группы: что в нём поменяли из чужой группы, иначе осталось бы
 * незамеченным. О новой команде сообщаем как о факте, о переименовании — как о заявке,
 * которую нужно разобрать.
 */
@Service
public class CatalogAlerts {

    private static final Logger log = LoggerFactory.getLogger(CatalogAlerts.class);

    private final AdminAccess admins;
    private final AppUserRepository users;
    private final NotificationRepository notifications;
    private final PushSender push;

    public CatalogAlerts(AdminAccess admins, AppUserRepository users,
                         NotificationRepository notifications, PushSender push) {
        this.admins = admins;
        this.users = users;
        this.notifications = notifications;
        this.push = push;
    }

    /** В справочнике появилась новая команда. */
    public void teamAdded(AppUser author, String teamName) {
        send("Справочник команд",
                author.getUsername() + " добавил команду «" + teamName + "»",
                "team-added:" + teamName + ":" + Instant.now().getEpochSecond());
    }

    /** Просят переименовать или убрать команду: пока не одобрите, в справочнике всё как было. */
    public void teamChangeAsked(AppUser author, String wasName, String nowName, boolean removal) {
        String body = removal
                ? author.getUsername() + " просит убрать команду «" + wasName + "»"
                : author.getUsername() + " просит переименовать «" + wasName + "» в «"
                        + nowName + "»";
        send("Заявка на справочник", body + ". Нужно одобрить или отклонить.",
                "team-change:" + wasName + ":" + Instant.now().getEpochSecond());
    }

    private void send(String title, String body, String key) {
        List<AppUser> all = users.findAll();
        List<Notification> fresh = new ArrayList<>();
        Instant now = Instant.now();
        for (AppUser user : all) {
            if (admins.isAdmin(user)) {
                fresh.add(new Notification(user.getId(), null, NotificationType.TEAM_CATALOG, key,
                        title, body, null, now));
            }
        }
        if (fresh.isEmpty()) {
            return; // владельца приложения в базе нет: некому и сообщать
        }
        notifications.saveAll(fresh);
        sendAfterCommit(fresh);
    }

    private void sendAfterCommit(List<Notification> fresh) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safePush(fresh);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safePush(fresh);
            }
        });
    }

    private void safePush(List<Notification> fresh) {
        try {
            push.push(fresh);
        } catch (RuntimeException e) {
            log.warn("Не удалось отправить оповещение о справочнике: {}", e.getMessage());
        }
    }
}
