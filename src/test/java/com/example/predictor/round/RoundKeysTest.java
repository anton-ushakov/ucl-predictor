package com.example.predictor.round;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Ключ тура: понедельник — первый тур недели, следующие дни — следующие туры. */
class RoundKeysTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    @Test
    void firstRoundKeepsTheMonday() {
        assertEquals(MONDAY, RoundKeys.keyFor(MONDAY, 1));
        assertEquals(1, RoundKeys.noOf(MONDAY));
        assertTrue(RoundKeys.isFirst(MONDAY));
    }

    @Test
    void nextRoundsTakeNextDaysOfTheSameWeek() {
        assertEquals(LocalDate.of(2026, 11, 24), RoundKeys.keyFor(MONDAY, 2));
        assertEquals(LocalDate.of(2026, 11, 29), RoundKeys.keyFor(MONDAY, 7));
        assertEquals(2, RoundKeys.noOf(LocalDate.of(2026, 11, 24)));
        assertEquals(7, RoundKeys.noOf(LocalDate.of(2026, 11, 29)));
    }

    @Test
    void everyKeyOfTheWeekPointsBackToItsMonday() {
        for (int no = 1; no <= RoundKeys.MAX_PER_WEEK; no++) {
            assertEquals(MONDAY, RoundKeys.weekOf(RoundKeys.keyFor(MONDAY, no)), "тур " + no);
        }
    }

    @Test
    void moreThanSevenRoundsAWeekIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> RoundKeys.keyFor(MONDAY, 8));
        assertThrows(IllegalArgumentException.class, () -> RoundKeys.keyFor(MONDAY, 0));
    }

    @Test
    void anyDayGivesTheRightWeek() {
        // ключ можно построить и от середины недели: понедельник вычислится сам
        assertEquals(LocalDate.of(2026, 11, 24), RoundKeys.keyFor(LocalDate.of(2026, 11, 26), 2));
    }
}
