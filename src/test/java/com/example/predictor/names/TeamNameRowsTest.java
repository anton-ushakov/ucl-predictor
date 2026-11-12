package com.example.predictor.names;

import com.example.predictor.names.TeamNamesService.TeamNameItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Список команд на экране словаря. */
class TeamNameRowsTest {

    private static final TeamNameMap DICTIONARY = TeamNameMap.of(Map.of(
            "Real Madrid", "Реал Мадрид",
            "Man City", "Манчестер Сити"
    ), true);

    @Test
    void teamsWithoutTranslationComeFirst() {
        List<TeamNameItem> rows = TeamNamesService.rows(
                List.of("Real Madrid", "Burnley", "Man City", "Alavés"), DICTIONARY);
        assertEquals(4, rows.size());
        assertEquals("Alavés", rows.get(0).original());
        assertEquals("Burnley", rows.get(1).original());
        assertNull(rows.get(0).russian());
        assertEquals("Манчестер Сити", rows.get(2).russian());
        assertEquals("Реал Мадрид", rows.get(3).russian());
    }

    @Test
    void sameTeamAppearsOnce() {
        List<TeamNameItem> rows = TeamNamesService.rows(
                List.of("Man City", "man city", " Man City "), DICTIONARY);
        assertEquals(1, rows.size());
        assertEquals("Man City", rows.get(0).original());
    }

    @Test
    void emptyAndMissingNamesAreSkipped() {
        // список приходит из базы, и одна кривая запись не должна ломать экран
        List<String> originals = new ArrayList<>();
        originals.add("Burnley");
        originals.add(null);
        originals.add("   ");
        List<TeamNameItem> rows = TeamNamesService.rows(originals, DICTIONARY);
        assertEquals(1, rows.size());
        assertEquals("Burnley", rows.get(0).original());
    }

    @Test
    void noMatchesGiveEmptyList() {
        assertEquals(List.of(), TeamNamesService.rows(List.of(), DICTIONARY));
    }
}
