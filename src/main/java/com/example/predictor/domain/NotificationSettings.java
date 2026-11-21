package com.example.predictor.domain;

import jakarta.persistence.*;

/** Какие оповещения хочет получать участник. По умолчанию включены все. */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "lock_soon", nullable = false)
    private boolean lockSoon = true;

    @Column(name = "match_finished", nullable = false)
    private boolean matchFinished = true;

    @Column(nullable = false)
    private boolean summary = true;

    @Column(nullable = false)
    private boolean discussion = true;

    @Column(nullable = false)
    private boolean chat = true;

    @Column(name = "polls", nullable = false)
    private boolean polls = true;

    protected NotificationSettings() {
    }

    public NotificationSettings(Long userId) {
        this.userId = userId;
    }

    public void change(boolean lockSoon, boolean matchFinished, boolean summary,
                       boolean discussion, boolean chat, boolean polls) {
        this.lockSoon = lockSoon;
        this.matchFinished = matchFinished;
        this.summary = summary;
        this.discussion = discussion;
        this.chat = chat;
        this.polls = polls;
    }

    public boolean wants(NotificationType type) {
        return switch (type) {
            case LOCK_SOON -> lockSoon;
            case MATCH_FINISHED -> matchFinished;
            case SUMMARY -> summary;
            case DISCUSSION -> discussion;
            case CHAT -> chat;
            case POLL -> polls;
            // о новой версии сообщаем всегда: выключать это участнику нечего
            case APP_UPDATE -> true;
            // без выбранных матчей тура не будет: владельцу напоминаем всегда
            case ROUND_SETUP -> true;
            // заявку на справочник некому разобрать, кроме владельца приложения
            case TEAM_CATALOG -> true;
        };
    }

    public Long getUserId() { return userId; }
    public boolean isLockSoon() { return lockSoon; }
    public boolean isMatchFinished() { return matchFinished; }
    public boolean isSummary() { return summary; }
    public boolean isDiscussion() { return discussion; }
    public boolean isChat() { return chat; }
    public boolean isPolls() { return polls; }
}
