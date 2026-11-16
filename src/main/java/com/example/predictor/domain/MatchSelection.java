package com.example.predictor.domain;

/**
 * Кто собирает состав тура. Владелец группы может собирать его всегда; настройка решает,
 * кто ещё. Нужна потому, что без собранного тура играть не во что, а владелец не всегда рядом.
 */
public enum MatchSelection {

    OWNER("Только владелец группы"),
    HELPERS("Владелец и помощники"),
    ANYONE("Любой участник группы");

    private final String description;

    MatchSelection(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
