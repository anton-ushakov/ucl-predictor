package com.example.predictor.scoring;

import com.example.predictor.scoring.Scoring.Hit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoringTest {

    private final Scoring standard = Scoring.DEFAULT;

    @Test
    void defaultRulesRepeatThePaperGame() {
        assertEquals(3, standard.exactScorePoints());
        assertEquals(0, standard.goalDiffPoints());
        assertEquals(1, standard.outcomePoints());
    }

    @Test
    void exactScoreIsRecognised() {
        assertEquals(Hit.EXACT, Scoring.hit(2, 1, 2, 1));
        assertEquals(3, standard.points(2, 1, 2, 1));
        assertEquals(3, standard.points(0, 0, 0, 0));
    }

    @Test
    void goalDifferenceIsItsOwnKindOfHit() {
        assertEquals(Hit.GOAL_DIFF, Scoring.hit(2, 1, 3, 2));
        assertEquals(Hit.GOAL_DIFF, Scoring.hit(1, 1, 2, 2));
        // отдельных баллов за разницу по умолчанию нет, но исход-то угадан: балл за исход
        assertEquals(1, standard.points(Hit.GOAL_DIFF));
        assertEquals(1, standard.points(2, 1, 3, 2));
    }

    @Test
    void outcomeOnly() {
        assertEquals(Hit.OUTCOME, Scoring.hit(1, 0, 3, 0));
        assertEquals(1, standard.points(1, 0, 3, 0));
    }

    @Test
    void wrongOutcomeIsAMiss() {
        assertEquals(Hit.MISS, Scoring.hit(2, 1, 1, 2));
        assertEquals(Hit.MISS, Scoring.hit(1, 1, 2, 0));
        assertEquals(0, standard.points(2, 1, 0, 3));
    }

    @Test
    void moreAccurateGuessNeverScoresLess() {
        // даже если настроить баллы «неправильно», точный счёт не может стоить меньше исхода
        Scoring odd = new Scoring(1, 0, 5);
        assertEquals(5, odd.points(2, 1, 2, 1));
        assertEquals(5, odd.points(2, 1, 3, 2));
        assertEquals(5, odd.points(1, 0, 3, 0));
    }

    @Test
    void customRulesChangeThePoints() {
        Scoring custom = new Scoring(5, 3, 2);
        assertEquals(5, custom.points(2, 1, 2, 1));
        assertEquals(3, custom.points(2, 1, 3, 2));
        assertEquals(2, custom.points(1, 0, 3, 0));
        assertEquals(0, custom.points(2, 1, 1, 2));
    }

    @Test
    void negativePointsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Scoring(-1, 0, 1));
    }

    @Test
    void descriptionMentionsGoalDifferenceOnlyWhenItCounts() {
        assertFalse(Scoring.DEFAULT.describe().contains("разница"));
        assertTrue(new Scoring(5, 3, 2).describe().contains("разница"));
    }
}
