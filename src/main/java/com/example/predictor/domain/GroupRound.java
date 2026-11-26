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
 * Тур группы.
 *
 * Ключ тура — дата, и это служебная метка, а не смысл: на ней держатся прогнозы, итоги,
 * долги и ссылки в оповещениях. Участник её не видит — в приложении тур подписан датами
 * своих матчей и названием.
 *
 * sourceKey говорит, из чего тур собран: «CL:5» — пятый тур Лиги чемпионов, «TOP:14» —
 * подборка топ-матчей. По нему автосостав второй раз находит свой тур и дополняет его,
 * а не заводит рядом ещё один. У тура, собранного руками, он пуст.
 */
@Entity
@Table(name = "group_rounds")
public class GroupRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "round_key", nullable = false)
    private LocalDate roundKey;

    @Column(name = "title", length = 60)
    private String title;

    @Column(name = "source_key", length = 40)
    private String sourceKey;

    /** Товарищеский тур: победитель есть, а шаурмы никто не должен. */
    @Column(name = "friendly", nullable = false)
    private boolean friendly;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected GroupRound() {
    }

    public GroupRound(Long groupId, LocalDate roundKey, String title, boolean friendly,
                      Instant createdAt, Long createdBy) {
        this(groupId, roundKey, title, friendly, createdAt, createdBy, null);
    }

    public GroupRound(Long groupId, LocalDate roundKey, String title, boolean friendly,
                      Instant createdAt, Long createdBy, String sourceKey) {
        this.sourceKey = sourceKey;
        this.groupId = groupId;
        this.roundKey = roundKey;
        this.title = title;
        this.friendly = friendly;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
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

    public String getSourceKey() {
        return sourceKey;
    }

    public String getTitle() {
        return title;
    }

    public void rename(String title) {
        this.title = title;
    }

    public boolean isFriendly() {
        return friendly;
    }

    /** Переключить тур между игрой на шаурму и товарищеским. */
    public void setFriendly(boolean friendly) {
        this.friendly = friendly;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
