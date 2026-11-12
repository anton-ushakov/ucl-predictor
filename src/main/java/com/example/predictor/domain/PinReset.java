package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Запись о том, что владелец группы сбросил участнику PIN-код. Чтобы это не прошло незаметно. */
@Entity
@Table(name = "pin_resets")
public class PinReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "reset_by", nullable = false, updatable = false)
    private Long resetBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PinReset() {
    }

    public PinReset(Long userId, Long groupId, Long resetBy, Instant createdAt) {
        this.userId = userId;
        this.groupId = groupId;
        this.resetBy = resetBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getGroupId() { return groupId; }
    public Long getResetBy() { return resetBy; }
    public Instant getCreatedAt() { return createdAt; }
}
