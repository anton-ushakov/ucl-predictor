package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Сообщение в обсуждении тура. Удалённое сообщение из базы не исчезает, а помечается:
 * так в ленте остаётся понятно, что здесь что-то было, и ничьи ответы не повисают в воздухе.
 */
@Entity
@Table(name = "round_messages")
public class RoundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "round_week", nullable = false, updatable = false)
    private LocalDate roundWeek;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RoundMessage() {
    }

    public RoundMessage(Long groupId, LocalDate roundWeek, Long userId, String text, Instant createdAt) {
        this.groupId = groupId;
        this.roundWeek = roundWeek;
        this.userId = userId;
        this.text = text;
        this.createdAt = createdAt;
    }

    public void delete(Instant at) {
        if (deletedAt == null) {
            deletedAt = at;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public Long getUserId() { return userId; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
