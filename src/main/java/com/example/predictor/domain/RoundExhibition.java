package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Участие в туре вне конкурса.
 *
 * Иногда играть хочется, а на шаурму — нет: уезжаешь, не успеваешь посмотреть матчи, не
 * хочешь долгов перед отпуском. Выпадать из сезона из-за этого обидно, поэтому есть середина:
 * баллы идут в общий зачёт, место в таблице тура видно, а шаурму такой участник не покупает и
 * не получает. На победителя и проигравшего тура он тоже не влияет — их определяют среди тех,
 * кто играет на шаурму.
 */
@Entity
@Table(name = "round_exhibition")
public class RoundExhibition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "round_key", nullable = false)
    private LocalDate roundKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoundExhibition() {
    }

    public RoundExhibition(Long groupId, LocalDate roundKey, Long userId, Instant createdAt) {
        this.groupId = groupId;
        this.roundKey = roundKey;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public LocalDate getRoundKey() {
        return roundKey;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
