package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Заявка на изменение команды в общем справочнике.
 *
 * Новую команду заводят сразу: она никому не мешает. А переименовать или убрать уже
 * существующую — дело общее: справочник один на все группы, и правка видна всем. Поэтому
 * такая правка ждёт одобрения владельца приложения.
 */
@Entity
@Table(name = "team_change_requests")
public class TeamChangeRequest {

    public enum Kind {
        /** Переименовать или поменять эмблему. */
        EDIT,
        /** Убрать команду из справочника. */
        DELETE
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private Kind kind;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "crest", length = 300)
    private String crest;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    protected TeamChangeRequest() {
    }

    public TeamChangeRequest(Long teamId, Long authorId, Kind kind, String name, String crest,
                             String countryCode, Instant createdAt) {
        this.teamId = teamId;
        this.authorId = authorId;
        this.kind = kind;
        this.name = name;
        this.crest = crest;
        this.countryCode = countryCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getCrest() {
        return crest;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void decide(Status status, Long adminId, Instant when) {
        this.status = status;
        this.decidedBy = adminId;
        this.decidedAt = when;
    }
}
