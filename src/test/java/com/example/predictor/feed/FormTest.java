package com.example.predictor.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Форма команды из турнирной таблицы: источник присылает её латиницей. */
class FormTest {

    @Test
    void lettersAreKeptForTheDots() {
        assertEquals("WWDLW", StandingsService.form("W,W,D,L,W"));
        assertEquals("L", StandingsService.form("L"));
    }

    @Test
    void emptyFormIsNothing() {
        assertNull(StandingsService.form(null));
        assertNull(StandingsService.form(""));
        assertNull(StandingsService.form(" , , "));
    }

    @Test
    void unknownLettersAreSkipped() {
        assertEquals("WD", StandingsService.form("W,X,D"));
    }
}
