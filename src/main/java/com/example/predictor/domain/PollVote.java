package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Голос участника: «да» или «нет». Пока голосование идёт, его можно переменить. */
@Entity
@Table(name = "poll_votes")
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "yes", nullable = false)
    private boolean yes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PollVote() {
    }

    public PollVote(Long pollId, Long userId, boolean yes, Instant createdAt) {
        this.pollId = pollId;
        this.userId = userId;
        this.yes = yes;
        this.createdAt = createdAt;
    }

    public Long getPollId() {
        return pollId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isYes() {
        return yes;
    }

    public void change(boolean yes, Instant when) {
        this.yes = yes;
        this.createdAt = when;
    }
}
