package com.example.predictor.standings;

import com.example.predictor.domain.Tiebreaker;
import com.example.predictor.scoring.Scoring;
import com.example.predictor.standings.StandingsCalculator.FinishedMatch;
import com.example.predictor.standings.StandingsCalculator.Guess;
import com.example.predictor.standings.StandingsCalculator.Row;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandingsCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-11-03T10:00:00Z");

    // Два матча: 2:1 и 0:0
    private final List<FinishedMatch> matches = List.of(
            new FinishedMatch(1, 2, 1),
            new FinishedMatch(2, 0, 0));

    private static Guess g(long user, long match, int h, int a, int minutes) {
        return new Guess(user, match, h, a, T0.plusSeconds(minutes * 60L));
    }

    private static Row row(List<Row> rows, long userId) {
        return rows.stream().filter(r -> r.userId() == userId).findFirst().orElseThrow();
    }

    @Test
    void pointsAndPlacesWithoutTiebreakers() {
        // user 1: 3 + 3 = 6; user 2: 1 + 1 = 2; user 3: 0 + 0 = 0
        List<Guess> guesses = List.of(
                g(1, 1, 2, 1, 0), g(1, 2, 0, 0, 0),
                g(2, 1, 1, 0, 0), g(2, 2, 1, 1, 0),
                g(3, 1, 0, 1, 0), g(3, 2, 1, 0, 0));
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L, 3L), matches, guesses, List.of(), Scoring.DEFAULT);

        assertEquals(List.of(1L, 2L, 3L), rows.stream().map(Row::userId).toList());
        assertEquals(6, row(rows, 1).points());
        assertEquals(2, row(rows, 2).points());
        assertEquals(0, row(rows, 3).points());
        assertTrue(row(rows, 1).winner());
        assertTrue(row(rows, 3).loser());
        assertFalse(row(rows, 2).winner());
        assertFalse(row(rows, 2).loser());
    }

    @Test
    void tieWithoutTiebreakersSharesPlaceAndBothAreWinners() {
        // user 1: точный счёт в матче 1 (3 балла), матч 2 пропущен = 3
        // user 2: точный счёт в матче 2 (3 балла), матч 1 пропущен = 3
        // user 3: мимо = 0
        List<Guess> tied = List.of(
                g(1, 1, 2, 1, 0),
                g(2, 2, 0, 0, 0),
                g(3, 1, 0, 3, 0));
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L, 3L), matches, tied, List.of(), Scoring.DEFAULT);
        assertEquals(1, row(rows, 1).place());
        assertEquals(1, row(rows, 2).place());
        assertEquals(3, row(rows, 3).place());
        assertTrue(row(rows, 1).winner());
        assertTrue(row(rows, 2).winner());
        assertTrue(row(rows, 3).loser());
    }

    @Test
    void exactScoresBreakTie() {
        // user 1: два исхода (1 + 1) + ... нужны равные баллы с разным числом точных.
        // user 1: 3 + 0 = 3 (один точный). user 2: 1 + 1 + ... возьмём три матча.
        List<FinishedMatch> three = List.of(
                new FinishedMatch(1, 2, 1),
                new FinishedMatch(2, 0, 0),
                new FinishedMatch(3, 1, 3));
        List<Guess> guesses = List.of(
                g(1, 1, 2, 1, 0), g(1, 2, 1, 0, 0), g(1, 3, 2, 1, 0),  // 3 + 0 + 0 = 3, один точный
                g(2, 1, 1, 0, 0), g(2, 2, 1, 1, 0), g(2, 3, 0, 1, 0)); // 1 + 1 + 1 = 3, точных нет

        List<Row> without = StandingsCalculator.calculate(List.of(1L, 2L), three, guesses, List.of(), Scoring.DEFAULT);
        assertEquals(3, row(without, 1).points());
        assertEquals(3, row(without, 2).points());
        assertEquals(1, row(without, 1).place());
        assertEquals(1, row(without, 2).place());
        assertFalse(row(without, 1).winner(), "когда все делят место, победителей нет");

        List<Row> with = StandingsCalculator.calculate(List.of(1L, 2L), three, guesses,
                List.of(Tiebreaker.EXACT_SCORES), Scoring.DEFAULT);
        assertEquals(1, row(with, 1).place());
        assertEquals(2, row(with, 2).place());
        assertTrue(row(with, 1).winner());
        assertTrue(row(with, 2).loser());
    }

    @Test
    void goalErrorBreaksTieWhenExactScoresEqual() {
        List<Guess> guesses = List.of(
                g(1, 1, 1, 0, 0), g(1, 2, 1, 1, 0),   // исход + исход = 2; ошибка 1+1 + 1 = ... см. ниже
                g(2, 1, 3, 0, 0), g(2, 2, 2, 2, 0));  // исход + исход = 2; ошибка большая
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L), matches, guesses,
                List.of(Tiebreaker.EXACT_SCORES, Tiebreaker.GOAL_ERROR), Scoring.DEFAULT);
        assertEquals(2, row(rows, 1).points());
        assertEquals(2, row(rows, 2).points());
        // user 1: |1-2|+|0-1| + |1-0|+|1-0| = 1+1+1+1 = 4; user 2: |3-2|+|0-1| + 2+2 = 1+1+2+2 = 6
        assertEquals(4, row(rows, 1).goalError());
        assertEquals(6, row(rows, 2).goalError());
        assertEquals(1, row(rows, 1).place());
        assertEquals(2, row(rows, 2).place());
    }

    @Test
    void missedPredictionCountsAsLargeError() {
        List<Guess> guesses = List.of(g(1, 1, 2, 1, 0));
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L), matches, guesses, List.of(), Scoring.DEFAULT);
        assertEquals(StandingsCalculator.MISSED_PREDICTION_ERROR, row(rows, 1).goalError());
        assertEquals(2 * StandingsCalculator.MISSED_PREDICTION_ERROR, row(rows, 2).goalError());
    }

    @Test
    void goalDifferenceHitsCounted() {
        List<Guess> guesses = List.of(g(1, 1, 3, 2, 0)); // разница +1 как в 2:1, но счёт не точный
        List<Row> rows = StandingsCalculator.calculate(List.of(1L), matches, guesses, List.of(), Scoring.DEFAULT);
        assertEquals(1, row(rows, 1).goalDiffHits());
        assertEquals(1, row(rows, 1).outcomeHits());
        assertEquals(0, row(rows, 1).exactScores());
    }

    @Test
    void earliestSubmissionBreaksTie() {
        List<Guess> guesses = List.of(
                g(1, 1, 2, 1, 30),   // сохранил позже
                g(2, 1, 2, 1, 5));   // сохранил раньше
        List<FinishedMatch> one = List.of(new FinishedMatch(1, 2, 1));
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L), one, guesses,
                List.of(Tiebreaker.EARLIEST_SUBMISSION), Scoring.DEFAULT);
        assertEquals(2, rows.get(0).userId());
        assertEquals(1, rows.get(0).place());
        assertEquals(2, rows.get(1).place());
    }

    @Test
    void everyoneTiedMeansNoWinnerAndNoLoser() {
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L, 3L), matches, List.of(), List.of(), Scoring.DEFAULT);
        for (Row r : rows) {
            assertEquals(1, r.place());
            assertFalse(r.winner());
            assertFalse(r.loser());
        }
    }

    @Test
    void severalLosersWhenTiedAtBottom() {
        List<Guess> guesses = List.of(g(1, 1, 2, 1, 0), g(1, 2, 0, 0, 0));
        List<Row> rows = StandingsCalculator.calculate(List.of(1L, 2L, 3L), matches, guesses, List.of(), Scoring.DEFAULT);
        assertTrue(row(rows, 1).winner());
        assertTrue(row(rows, 2).loser());
        assertTrue(row(rows, 3).loser());
        assertEquals(2, row(rows, 2).place());
        assertEquals(2, row(rows, 3).place());
    }
}
