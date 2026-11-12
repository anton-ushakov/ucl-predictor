package com.example.predictor.domain;

/** Виды оповещений. Каждый участник включает и выключает их по отдельности. */
public enum NotificationType {

    /** За час до закрытия приёма прогнозов, если прогноз ещё не сохранён. */
    LOCK_SOON("Час до закрытия приёма прогнозов"),

    /** Матч сыгран: счёт и сколько баллов он вам принёс. */
    MATCH_FINISHED("Итог каждого матча со счётом и баллами"),

    /** Итоги игрового дня и итоги тура. */
    SUMMARY("Итоги дня и итоги тура");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
