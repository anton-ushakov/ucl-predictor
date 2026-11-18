package com.example.predictor.round;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Подсчёты для итогов сезона. */
class SeasonReportTest {

    @Test
    void streakCountsRoundsInARow() {
        assertEquals(3, SeasonReportService.longestWinStreak(
                List.of(true, false, true, true, true, false)));
        assertEquals(1, SeasonReportService.longestWinStreak(List.of(false, true, false)));
        assertEquals(0, SeasonReportService.longestWinStreak(List.of(false, false)));
        assertEquals(0, SeasonReportService.longestWinStreak(List.of()));
    }

    @Test
    void wordsAgreeWithNumbers() {
        assertEquals("балл", SeasonReportService.pointWord(1));
        assertEquals("балла", SeasonReportService.pointWord(23));
        assertEquals("баллов", SeasonReportService.pointWord(11));
        assertEquals("тур", SeasonReportService.roundWord(21));
        assertEquals("тура", SeasonReportService.roundWord(4));
        assertEquals("туров", SeasonReportService.roundWord(15));
        assertEquals("точный счёт", SeasonReportService.exactWord(1));
        assertEquals("точных счёта", SeasonReportService.exactWord(3));
        assertEquals("точных счетов", SeasonReportService.exactWord(12));
    }
}
