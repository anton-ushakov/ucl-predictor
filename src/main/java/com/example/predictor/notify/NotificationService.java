package com.example.predictor.notify;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Notification;
import com.example.predictor.domain.NotificationSettings;
import com.example.predictor.domain.NotificationType;
import com.example.predictor.repository.NotificationRepository;
import com.example.predictor.repository.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Оповещения участника: список, отметка о прочтении и настройки. */
@Service
public class NotificationService {

    public record NotificationDto(Long id, Long groupId, NotificationType type, String title,
                                  String body, LocalDate roundWeek, Instant createdAt, boolean read) {
    }

    public record SettingsDto(boolean lockSoon, boolean matchFinished, boolean summary,
                              boolean discussion, boolean chat, boolean polls) {
    }

    public record OptionDto(String code, String description) {
    }

    /**
     * С какой сборки приложение умеет показывать этот вид оповещений. Старому приложению
     * настройку не показываем: экрана под неё там нет, а переключатель без экрана сбивает
     * с толку и, хуже того, в старых сборках переключал соседнюю настройку.
     */
    private static int sinceVersion(NotificationType type) {
        return switch (type) {
            case CHAT, POLL, TEAM_CATALOG, ACCOUNT_REQUEST -> 20;
            default -> 0;
        };
    }

    /** Какие настройки показывать этому приложению и этому человеку. */
    private List<OptionDto> optionsFor(AppUser user, Integer clientVersion) {
        int version = clientVersion == null ? 0 : clientVersion;
        List<OptionDto> list = new java.util.ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            if (version < sinceVersion(type)) {
                continue;
            }
            // заявки на справочник разбирает владелец приложения: остальным эта строка ни к чему
            if ((type == NotificationType.TEAM_CATALOG
                    || type == NotificationType.ACCOUNT_REQUEST) && !admins.isAdmin(user)) {
                continue;
            }
            list.add(new OptionDto(type.name(), type.getDescription()));
        }
        return list;
    }

    public record InboxDto(long unread, List<NotificationDto> notifications, SettingsDto settings,
                           List<OptionDto> types) {
    }

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final com.example.predictor.common.AdminAccess admins;

    public NotificationService(NotificationRepository notifications,
                               NotificationSettingsRepository settings,
                               com.example.predictor.common.AdminAccess admins) {
        this.notifications = notifications;
        this.settings = settings;
        this.admins = admins;
    }

    @Transactional(readOnly = true)
    public InboxDto inbox(AppUser user, boolean unreadOnly, Integer clientVersion) {
        List<Notification> list = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user.getId())
                : notifications.findTop100ByUserIdOrderByCreatedAtDesc(user.getId());
        List<NotificationDto> dtos = list.stream()
                .map(n -> new NotificationDto(n.getId(), n.getGroupId(), n.getType(), n.getTitle(),
                        n.getBody(), n.getRoundWeek(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
        return new InboxDto(notifications.countByUserIdAndReadAtIsNull(user.getId()), dtos,
                toDto(settingsOf(user.getId())), optionsFor(user, clientVersion));
    }

    @Transactional
    public void markRead(AppUser user, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            notifications.markAllRead(user.getId(), Instant.now());
        } else {
            notifications.markRead(user.getId(), ids, Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public SettingsDto settings(AppUser user) {
        return toDto(settingsOf(user.getId()));
    }

    @Transactional
    public SettingsDto updateSettings(AppUser user, boolean lockSoon, boolean matchFinished,
                                      boolean summary, boolean discussion, boolean chat,
                                      boolean polls) {
        NotificationSettings s = settings.findById(user.getId())
                .orElseGet(() -> new NotificationSettings(user.getId()));
        s.change(lockSoon, matchFinished, summary, discussion, chat, polls);
        settings.save(s);
        return toDto(s);
    }

    private NotificationSettings settingsOf(Long userId) {
        // по умолчанию включены все три вида
        return settings.findById(userId).orElseGet(() -> new NotificationSettings(userId));
    }

    private static SettingsDto toDto(NotificationSettings s) {
        return new SettingsDto(s.isLockSoon(), s.isMatchFinished(), s.isSummary(),
                s.isDiscussion(), s.isChat(), s.isPolls());
    }
}
