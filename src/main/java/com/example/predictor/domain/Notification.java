package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Готовое оповещение для участника. Телефон забирает их и показывает. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private NotificationType type;

    /** Ключ, по которому одно и то же оповещение не присылается дважды. */
    @Column(name = "dedup_key", nullable = false, updatable = false, length = 160)
    private String dedupKey;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(nullable = false, updatable = false, length = 600)
    private String body;

    @Column(name = "round_week", updatable = false)
    private LocalDate roundWeek;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(Long userId, Long groupId, NotificationType type, String dedupKey,
                        String title, String body, LocalDate roundWeek, Instant createdAt) {
        this.userId = userId;
        this.groupId = groupId;
        this.type = type;
        this.dedupKey = dedupKey;
        this.title = title;
        this.body = body;
        this.roundWeek = roundWeek;
        this.createdAt = createdAt;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getGroupId() { return groupId; }
    public NotificationType getType() { return type; }
    public String getDedupKey() { return dedupKey; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
