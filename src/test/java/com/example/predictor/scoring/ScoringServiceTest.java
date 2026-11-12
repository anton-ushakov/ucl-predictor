package com.example.predictor.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

    @Test
    void exactScoreGivesThreePoints() {
        assertEquals(3, scoring.points(2, 1, 2, 1));
        assertEquals(3, scoring.points(0, 0, 0, 0));
    }

    @Test
    void correctHomeWinOutcomeGivesOnePoint() {
        assertEquals(1, scoring.points(3, 0, 2, 1));
    }

    @Test
    void correctAwayWinOutcomeGivesOnePoint() {
        assertEquals(1, scoring.points(0, 1, 1, 4));
    }

    @Test
    void correctDrawWithWrongScoreGivesOnePoint() {
        assertEquals(1, scoring.points(1, 1, 2, 2));
    }

    @Test
    void wrongOutcomeGivesZero() {
        assertEquals(0, scoring.points(2, 0, 0, 1));
        assertEquals(0, scoring.points(1, 1, 1, 0));
        assertEquals(0, scoring.points(0, 2, 1, 1));
    }
}
