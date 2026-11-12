package com.example.predictor.domain;

public enum MatchStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    POSTPONED,
    CANCELLED;

    /** Матч больше не изменится: сыгран или отменён. */
    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
