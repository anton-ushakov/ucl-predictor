package com.example.predictor.notify;

import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.Notification;
import com.example.predictor.domain.NotificationType;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.NotificationRepository;
import com.example.predictor.push.PushSender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Итоги тура пересчитаны: счёт матча оказался другим.
 *
 * Про это нужно сказать обязательно и всем. Люди уже видели таблицу, обсудили её и, может
 * быть, договорились о шаурме — молча подменить итоги было бы хуже самой ошибки. Поэтому
 * оповещение идёт как итог тура и не смотрит на настройки: отключить «итоги тура» и не
 * узнать, что они изменились, — плохая пара.
 */
@Service
public class RecountAlerts {

    private static final Logger log = LoggerFactory.getLogger(RecountAlerts.class);

    private final GroupMemberRepository members;
    private final NotificationRepository notifications;
    private final PushSender push;

    public RecountAlerts(GroupMemberRepository members, NotificationRepository notifications,
                         PushSender push) {
        this.members = members;
        this.notifications = notifications;
        this.push = push;
    }

    /** reason — почему пересчитали, человеческими словами: его и увидят в оповещении. */
    public void recounted(FriendGroup group, LocalDate roundKey, String reason, Instant now) {
        List<Notification> fresh = new ArrayList<>();
        // ключ с временем: пересчёт может случиться и во второй раз, и о нём тоже надо сказать
        String key = "recount:" + group.getId() + ":" + roundKey + ":" + now.toEpochMilli();
        String body = reason + " Баллы и место в таблице тура могли измениться — посмотрите "
                + "итоги заново.";
        for (GroupMember gm : members.findAllWithUser(group.getId())) {
            if (gm.getUser().isBlocked()) {
                continue;
            }
            fresh.add(new Notification(gm.getUser().getId(), group.getId(),
                    NotificationType.SUMMARY, key, "Итоги тура пересчитаны", body, roundKey, now));
        }
        if (fresh.isEmpty()) {
            return;
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
            // не доставили — новые итоги всё равно видны в приложении
            log.warn("Не удалось отправить оповещение о пересчёте: {}", e.getMessage());
        }
    }
}
