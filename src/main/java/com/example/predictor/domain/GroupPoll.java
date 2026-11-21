package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Вопрос группе с ответом «да» или «нет».
 *
 * openVotes=true — открытое голосование: после завершения видно, кто как ответил.
 * Пока голосование идёт, этого не видно никогда: иначе первые голоса тянули бы за собой
 * остальные.
 */
@Entity
@Table(name = "group_polls")
public class GroupPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "question", nullable = false, length = 200)
    private String question;

    @Column(name = "open_votes", nullable = false)
    private boolean openVotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected GroupPoll() {
    }

    public GroupPoll(Long groupId, Long authorId, String question, boolean openVotes,
                     Instant createdAt, Instant closesAt) {
        this.groupId = groupId;
        this.authorId = authorId;
        this.question = question;
        this.openVotes = openVotes;
        this.createdAt = createdAt;
        this.closesAt = closesAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getQuestion() {
        return question;
    }

    public boolean isOpenVotes() {
        return openVotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    /** Голосование закончилось: закрыли руками или вышел срок. */
    public boolean isClosed(Instant now) {
        return closedAt != null || (closesAt != null && !closesAt.isAfter(now));
    }

    public void close(Instant when) {
        if (closedAt == null) {
            this.closedAt = when;
        }
    }
}
