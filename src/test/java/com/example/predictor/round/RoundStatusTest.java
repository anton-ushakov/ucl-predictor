package com.example.predictor.round;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundStatusTest {

    @Test
    void notStartedYet() {
        assertEquals(RoundStatus.UPCOMING, RoundStatus.of(false, false, false, false));
        assertEquals(RoundStatus.UPCOMING, RoundStatus.of(false, false, false, true));
    }

    @Test
    void startedButNotFinalized() {
        assertEquals(RoundStatus.IN_PROGRESS, RoundStatus.of(false, true, false, true));
        // все матчи сыграны, прогнозы есть: итоги вот-вот подведут
        assertEquals(RoundStatus.IN_PROGRESS, RoundStatus.of(false, true, true, true));
    }

    @Test
    void allMatchesOverWithoutPredictionsIsNotInProgress() {
        assertEquals(RoundStatus.NO_PREDICTIONS, RoundStatus.of(false, true, true, false));
    }

    @Test
    void finalizedWins() {
        assertEquals(RoundStatus.FINISHED, RoundStatus.of(true, true, true, true));
        assertEquals(RoundStatus.FINISHED, RoundStatus.of(true, true, true, false));
    }
}
