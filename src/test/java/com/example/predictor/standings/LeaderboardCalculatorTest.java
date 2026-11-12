package com.example.predictor.standings;

import com.example.predictor.standings.LeaderboardCalculator.ResultLine;
import com.example.predictor.standings.LeaderboardCalculator.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardCalculatorTest {

    @Test
    void sumsPointsWinsAndLossesAcrossRounds() {
        List<Row> rows = LeaderboardCalculator.calculate(List.of(
                new ResultLine(1, 7, 2, true, false),
                new ResultLine(2, 4, 1, false, true),
                new ResultLine(1, 3, 0, false, true),
                new ResultLine(2, 5, 2, true, false)));
        assertEquals(2, rows.size());
        Row first = rows.get(0);
        assertEquals(1, first.userId());
        assertEquals(10, first.totalPoints());
        assertEquals(2, first.exactScores());
        assertEquals(1, first.wins());
        assertEquals(1, first.losses());
        assertEquals(2, first.rounds());
        assertEquals(1, first.place());
        assertEquals(2, rows.get(1).place());
    }

    @Test
    void equalPointsAreSeparatedByExactScoresThenWins() {
        List<Row> rows = LeaderboardCalculator.calculate(List.of(
                new ResultLine(1, 5, 1, false, false),
                new ResultLine(2, 5, 2, false, false)));
        assertEquals(2, rows.get(0).userId());
        assertEquals(1, rows.get(0).place());
        assertEquals(2, rows.get(1).place());
    }

    @Test
    void completelyEqualParticipantsShareThePlace() {
        List<Row> rows = LeaderboardCalculator.calculate(List.of(
                new ResultLine(1, 5, 1, false, false),
                new ResultLine(2, 5, 1, false, false),
                new ResultLine(3, 2, 0, false, false)));
        assertEquals(1, rows.get(0).place());
        assertEquals(1, rows.get(1).place());
        assertEquals(3, rows.get(2).place());
    }

    @Test
    void noRoundsGivesEmptyBoard() {
        assertTrue(LeaderboardCalculator.calculate(List.of()).isEmpty());
    }
}
