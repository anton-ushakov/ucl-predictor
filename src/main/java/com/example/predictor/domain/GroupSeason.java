package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Сезон группы: с какой недели считается общий зачёт. */
@Entity
@Table(name = "group_seasons")
public class GroupSeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "start_week", nullable = false, updatable = false)
    private LocalDate startWeek;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected GroupSeason() {
    }

    public GroupSeason(Long groupId, String name, LocalDate startWeek, Instant createdAt,
                       Long createdBy) {
        this.groupId = groupId;
        this.name = name;
        this.startWeek = startWeek;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getName() { return name; }
    public LocalDate getStartWeek() { return startWeek; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
}
