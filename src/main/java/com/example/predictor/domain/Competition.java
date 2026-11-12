package com.example.predictor.domain;

import jakarta.persistence.*;

/**
 * Турнир. Для тех, что есть в бесплатном тарифе football-data.org, матчи загружаются сами
 * (synced=true, code — обозначение в источнике). Для остальных, вроде Лиги Европы и чемпионата
 * России, владелец группы добавляет матчи руками.
 */
@Entity
@Table(name = "competitions")
public class Competition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Обозначение в football-data.org (CL, PL, …). У ручных турниров его нет. */
    @Column(length = 10, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", nullable = false, length = 40)
    private String shortName;

    /** Матчи загружаются автоматически. */
    @Column(nullable = false)
    private boolean synced = true;

    /** Выключенный турнир не загружается и не предлагается при отборе матчей. */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    protected Competition() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
    public boolean isSynced() { return synced; }
    public boolean isEnabled() { return enabled; }
    public int getSortOrder() { return sortOrder; }
}
