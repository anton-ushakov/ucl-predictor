package com.example.predictor.notify;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.Notification;
import com.example.predictor.domain.NotificationSettings;
import com.example.predictor.domain.NotificationType;
import com.example.predictor.notify.NotificationTexts.Text;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.NotificationRepository;
import com.example.predictor.repository.NotificationSettingsRepository;
import com.example.predictor.push.PushSender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Оповещения об обсуждении тура — сразу, а не раз в полчаса.
 *
 * Раньше их готовил общий сборщик по расписанию и складывал реплики в одно сообщение.
 * Для спора это слишком медленно: пока оповещение дойдёт, разговор успевает кончиться.
 * Поэтому сообщение в обсуждении само будит остальных, и каждое — отдельно.
 *
 * Автору про его же реплику не пишем, а тому, кто обсуждение отключил, не пишем вовсе.
 */
@Service
public class DiscussionAlerts {

    private static final Logger log = LoggerFactory.getLogger(DiscussionAlerts.class);

    private final GroupMemberRepository members;
    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final PushSender push;

    public DiscussionAlerts(GroupMemberRepository members, NotificationRepository notifications,
                            NotificationSettingsRepository settings, PushSender push) {
        this.members = members;
        this.notifications = notifications;
        this.settings = settings;
        this.push = push;
    }

    /** Написано сообщение: будим остальных участников группы. */
    public void newMessage(FriendGroup group, LocalDate week, AppUser author, Long messageId,
                           String text, Instant writtenAt) {
        List<GroupMember> memberList = members.findAllWithUser(group.getId());
        if (memberList.size() < 2) {
            return;
        }
        Map<Long, NotificationSettings> wants = settingsOf(memberList);

        Text message = NotificationTexts.discussion(group.getName(), week,
                author.getUsername(), text);
        // ключ на каждое сообщение: иначе второе за тот же час потерялось бы как повтор
        String key = "discussion:" + group.getId() + ":" + week + ":msg:" + messageId;

        List<Notification> fresh = new ArrayList<>();
        for (GroupMember gm : memberList) {
            Long userId = gm.getUser().getId();
            if (userId.equals(author.getId())) {
                continue;
            }
            NotificationSettings prefs = wants.get(userId);
            if (prefs != null && !prefs.wants(NotificationType.DISCUSSION)) {
                continue;
            }
            fresh.add(new Notification(userId, group.getId(), NotificationType.DISCUSSION, key,
                    message.title(), message.body(), week, writtenAt));
        }
        if (fresh.isEmpty()) {
            return;
        }
        notifications.saveAll(fresh);
        sendAfterCommit(fresh);
    }

    /** Написано сообщение в чате группы: будим остальных так же, как в обсуждении тура. */
    public void newChatMessage(FriendGroup group, AppUser author, Long messageId, String text,
                               Instant writtenAt) {
        List<GroupMember> memberList = members.findAllWithUser(group.getId());
        if (memberList.size() < 2) {
            return;
        }
        Map<Long, NotificationSettings> wants = settingsOf(memberList);
        Text message = NotificationTexts.chat(group.getName(), author.getUsername(), text);
        String key = "chat:" + group.getId() + ":msg:" + messageId;

        List<Notification> fresh = new ArrayList<>();
        for (GroupMember gm : memberList) {
            Long userId = gm.getUser().getId();
            if (userId.equals(author.getId())) {
                continue;
            }
            NotificationSettings prefs = wants.get(userId);
            if (prefs != null && !prefs.wants(NotificationType.CHAT)) {
                continue;
            }
            fresh.add(new Notification(userId, group.getId(), NotificationType.CHAT, key,
                    message.title(), message.body(), null, writtenAt));
        }
        if (fresh.isEmpty()) {
            return;
        }
        notifications.saveAll(fresh);
        sendAfterCommit(fresh);
    }

    private Map<Long, NotificationSettings> settingsOf(List<GroupMember> memberList) {
        Map<Long, NotificationSettings> wants = new HashMap<>();
        List<Long> ids = memberList.stream().map(gm -> gm.getUser().getId()).toList();
        for (NotificationSettings s : settings.findByUserIdIn(ids)) {
            wants.put(s.getUserId(), s);
        }
        return wants;
    }

    /**
     * На телефоны отправляем после того, как сообщение действительно записано: оповещение
     * о реплике, которой в итоге не оказалось, хуже, чем оповещение с опозданием в секунду.
     */
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
            // не доставили — сообщение всё равно записано и видно в приложении
            log.warn("Не удалось отправить оповещение об обсуждении: {}", e.getMessage());
        }
    }
}
