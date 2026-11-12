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
import java.util.Arrays;
import java.util.List;

/** Оповещения участника: список, отметка о прочтении и настройки. */
@Service
public class NotificationService {

    public record NotificationDto(Long id, Long groupId, NotificationType type, String title,
                                  String body, LocalDate roundWeek, Instant createdAt, boolean read) {
    }

    public record SettingsDto(boolean lockSoon, boolean matchFinished, boolean summary) {
    }

    public record OptionDto(String code, String description) {
    }

    public record InboxDto(long unread, List<NotificationDto> notifications, SettingsDto settings,
                           List<OptionDto> types) {
    }

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;

    public NotificationService(NotificationRepository notifications,
                               NotificationSettingsRepository settings) {
        this.notifications = notifications;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public InboxDto inbox(AppUser user, boolean unreadOnly) {
        List<Notification> list = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user.getId())
                : notifications.findTop100ByUserIdOrderByCreatedAtDesc(user.getId());
        List<NotificationDto> dtos = list.stream()
                .map(n -> new NotificationDto(n.getId(), n.getGroupId(), n.getType(), n.getTitle(),
                        n.getBody(), n.getRoundWeek(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
        return new InboxDto(notifications.countByUserIdAndReadAtIsNull(user.getId()), dtos,
                toDto(settingsOf(user.getId())),
                Arrays.stream(NotificationType.values())
                        .map(t -> new OptionDto(t.name(), t.getDescription())).toList());
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
                                      boolean summary) {
        NotificationSettings s = settings.findById(user.getId())
                .orElseGet(() -> new NotificationSettings(user.getId()));
        s.change(lockSoon, matchFinished, summary);
        settings.save(s);
        return toDto(s);
    }

    private NotificationSettings settingsOf(Long userId) {
        // по умолчанию включены все три вида
        return settings.findById(userId).orElseGet(() -> new NotificationSettings(userId));
    }

    private static SettingsDto toDto(NotificationSettings s) {
        return new SettingsDto(s.isLockSoon(), s.isMatchFinished(), s.isSummary());
    }
}
