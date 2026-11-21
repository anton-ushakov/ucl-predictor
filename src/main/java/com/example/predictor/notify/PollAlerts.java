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
 * Оповещение о новом голосовании.
 *
 * Голосование без голосов бессмысленно, а заметить его в приложении самому сложно: участник
 * заходит ради прогнозов. Поэтому про новый вопрос сообщаем сразу — один раз, тому, кого
 * спрашивают, и не автору.
 */
@Service
public class PollAlerts {

    private static final Logger log = LoggerFactory.getLogger(PollAlerts.class);

    private final GroupMemberRepository members;
    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final PushSender push;

    public PollAlerts(GroupMemberRepository members, NotificationRepository notifications,
                      NotificationSettingsRepository settings, PushSender push) {
        this.members = members;
        this.notifications = notifications;
        this.settings = settings;
        this.push = push;
    }

    public void newPoll(FriendGroup group, AppUser author, Long pollId, String question,
                        Instant createdAt) {
        List<GroupMember> memberList = open(members.findAllWithUser(group.getId()));
        if (memberList.size() < 2) {
            return;
        }
        Map<Long, NotificationSettings> wants = new HashMap<>();
        List<Long> ids = memberList.stream().map(gm -> gm.getUser().getId()).toList();
        for (NotificationSettings s : settings.findByUserIdIn(ids)) {
            wants.put(s.getUserId(), s);
        }

        Text message = NotificationTexts.poll(group.getName(), author.getUsername(), question);
        String key = "poll:" + group.getId() + ":" + pollId;

        List<Notification> fresh = new ArrayList<>();
        for (GroupMember gm : memberList) {
            Long userId = gm.getUser().getId();
            if (userId.equals(author.getId())) {
                continue;
            }
            NotificationSettings prefs = wants.get(userId);
            if (prefs != null && !prefs.wants(NotificationType.POLL)) {
                continue;
            }
            fresh.add(new Notification(userId, group.getId(), NotificationType.POLL, key,
                    message.title(), message.body(), null, createdAt));
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
            // не доставили — голосование всё равно видно в приложении
            log.warn("Не удалось отправить оповещение о голосовании: {}", e.getMessage());
        }
    }

    /** Без тех, кому закрыли доступ: оповещения им не нужны и не дойдут. */
    private static List<GroupMember> open(List<GroupMember> memberList) {
        List<GroupMember> list = new java.util.ArrayList<>(memberList.size());
        for (GroupMember gm : memberList) {
            if (!gm.getUser().isBlocked()) {
                list.add(gm);
            }
        }
        return list;
    }
}
