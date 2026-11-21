package com.example.predictor.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Форма команды из турнирной таблицы: источник присылает её латиницей. */
class FormTest {

    @Test
    void lettersBecomeRussian() {
        assertEquals("В В Н П В", StandingsService.form("W,W,D,L,W"));
        assertEquals("П", StandingsService.form("L"));
    }

    @Test
    void emptyFormIsNothing() {
        assertNull(StandingsService.form(null));
        assertNull(StandingsService.form(""));
        assertNull(StandingsService.form(" , , "));
    }

    @Test
    void unknownLettersAreSkipped() {
        assertEquals("В Н", StandingsService.form("W,X,D"));
    }
}
