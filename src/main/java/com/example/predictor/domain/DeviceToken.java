package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Адрес телефона в Firebase, по которому ему можно отправить оповещение.
 * Выдаёт его сам Firebase, приложение передаёт сюда при входе.
 */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500, unique = true)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Когда приложение в последний раз подтвердило этот адрес. */
    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected DeviceToken() {
    }

    public DeviceToken(Long userId, String token, Instant now) {
        this.userId = userId;
        this.token = token;
        this.createdAt = now;
        this.seenAt = now;
    }

    /** Телефон мог перейти к другому участнику: адрес остаётся, хозяин меняется. */
    public void touch(Long userId, Instant now) {
        this.userId = userId;
        this.seenAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getToken() { return token; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSeenAt() { return seenAt; }
}
