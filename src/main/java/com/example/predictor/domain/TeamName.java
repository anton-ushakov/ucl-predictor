package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Русское название команды. original — название так, как его присылает football-data.org
 * или как его ввёл владелец для ручного матча; сравнение идёт без учёта регистра.
 * Словарь общий для всех групп: перевод, который добавил один владелец, видят все.
 */
@Entity
@Table(name = "team_names")
public class TeamName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String original;

    @Column(nullable = false, length = 100)
    private String russian;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TeamName() {
    }

    public TeamName(String original, String russian, Long updatedBy, Instant updatedAt) {
        this.original = original;
        this.russian = russian;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void rename(String russian, Long updatedBy, Instant updatedAt) {
        this.russian = russian;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getOriginal() { return original; }
    public String getRussian() { return russian; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
