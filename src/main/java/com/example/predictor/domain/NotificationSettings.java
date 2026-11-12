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

    protected NotificationSettings() {
    }

    public NotificationSettings(Long userId) {
        this.userId = userId;
    }

    public void change(boolean lockSoon, boolean matchFinished, boolean summary) {
        this.lockSoon = lockSoon;
        this.matchFinished = matchFinished;
        this.summary = summary;
    }

    public boolean wants(NotificationType type) {
        return switch (type) {
            case LOCK_SOON -> lockSoon;
            case MATCH_FINISHED -> matchFinished;
            case SUMMARY -> summary;
        };
    }

    public Long getUserId() { return userId; }
    public boolean isLockSoon() { return lockSoon; }
    public boolean isMatchFinished() { return matchFinished; }
    public boolean isSummary() { return summary; }
}
