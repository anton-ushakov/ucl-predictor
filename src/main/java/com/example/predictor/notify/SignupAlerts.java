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
 * Оповещение владельцу приложения о новой заявке на регистрацию.
 *
 * Заявка без ответа — это человек, который стоит под дверью: он уже ввёл имя и пароль,
 * но войти не может, пока ему не откроют. Поэтому сообщаем сразу.
 */
@Service
public class SignupAlerts {

    private static final Logger log = LoggerFactory.getLogger(SignupAlerts.class);

    private final AdminAccess admins;
    private final AppUserRepository users;
    private final NotificationRepository notifications;
    private final PushSender push;

    public SignupAlerts(AdminAccess admins, AppUserRepository users,
                        NotificationRepository notifications, PushSender push) {
        this.admins = admins;
        this.users = users;
        this.notifications = notifications;
        this.push = push;
    }

    public void newSignup(AppUser applicant) {
        Instant now = Instant.now();
        String key = "signup:" + applicant.getId();
        List<Notification> fresh = new ArrayList<>();
        for (AppUser user : users.findAll()) {
            if (!admins.isAdmin(user) || user.isBlocked()) {
                continue;
            }
            fresh.add(new Notification(user.getId(), null, NotificationType.ACCOUNT_REQUEST, key,
                    "Заявка на регистрацию",
                    applicant.getUsername() + " просит доступ к приложению. "
                            + "Одобрите или отклоните в настройках.",
                    null, now));
        }
        if (fresh.isEmpty()) {
            return;
        }
        notifications.saveAll(fresh);
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
            log.warn("Не удалось отправить оповещение о заявке: {}", e.getMessage());
        }
    }
}
