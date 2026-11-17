package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Команда из справочника: клуб или сборная. Нужна для матчей, которых нет в источнике:
 * один раз заведённую команду дальше выбирают из списка, а не набирают заново.
 */
@Entity
@Table(name = "teams")
public class Team {

    public enum Kind {
        CLUB, NATIONAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, length = 100)
    private String name;

    /** Как команда называется в источнике: по нему подхватываются эмблемы загруженных матчей. */
    @Column(name = "original_name", length = 100)
    private String originalName;

    /** Прямая ссылка на эмблему. У сборных её может не быть — тогда флаг строится по коду страны. */
    @Column(length = 300)
    private String crest;

    /** Код страны для флага: ru, de, gb-eng. */
    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    protected Team() {
    }

    public Team(Kind kind, String name, String crest, String countryCode, Long createdBy) {
        this.kind = kind;
        this.name = name;
        this.crest = crest;
        this.countryCode = countryCode;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void change(String name, String crest, String countryCode) {
        this.name = name;
        this.crest = crest;
        this.countryCode = countryCode;
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public String getName() { return name; }
    public String getOriginalName() { return originalName; }
    public String getCrest() { return crest; }
    public String getCountryCode() { return countryCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
}
