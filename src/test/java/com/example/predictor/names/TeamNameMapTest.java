package com.example.predictor.names;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamNameMapTest {

    private static TeamNameMap russian() {
        return TeamNameMap.of(Map.of(
                "Real Madrid", "Реал Мадрид",
                "Man City", "Манчестер Сити",
                " Zenit ", "Зенит"
        ), true);
    }

    @Test
    void knownTeamIsShownInRussian() {
        assertEquals("Реал Мадрид", russian().display("Real Madrid"));
    }

    @Test
    void caseAndSpacesDoNotMatter() {
        TeamNameMap map = russian();
        assertEquals("Манчестер Сити", map.display("man city"));
        assertEquals("Манчестер Сити", map.display("  MAN CITY  "));
        assertEquals("Зенит", map.display("Zenit"));
    }

    @Test
    void unknownTeamKeepsOriginalName() {
        assertEquals("Burnley", russian().display("Burnley"));
    }

    @Test
    void groupCanTurnRussianNamesOff() {
        TeamNameMap off = TeamNameMap.of(Map.of("Real Madrid", "Реал Мадрид"), false);
        assertEquals("Real Madrid", off.display("Real Madrid"));
    }

    @Test
    void emptyDictionaryShowsEverythingAsIs() {
        assertEquals("Real Madrid", TeamNameMap.EMPTY.display("Real Madrid"));
        assertEquals(0, TeamNameMap.EMPTY.size());
    }

    @Test
    void blankTranslationIsIgnored() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("Everton", "  ");
        pairs.put("Fulham", null);
        TeamNameMap map = TeamNameMap.of(pairs, true);
        assertEquals("Everton", map.display("Everton"));
        assertFalse(map.has("Everton"));
        assertFalse(map.has("Fulham"));
    }

    @Test
    void screenSeesWhichTeamsNeedTranslation() {
        TeamNameMap map = russian();
        assertTrue(map.has("real madrid"));
        assertFalse(map.has("Burnley"));
        assertEquals("Зенит", map.russianOf("zenit"));
        assertNull(map.russianOf("Burnley"));
    }

    @Test
    void nullNameDoesNotBreakDisplay() {
        assertNull(russian().display(null));
    }

    @Test
    void severalOriginalSpellingsLeadToOneName() {
        TeamNameMap map = TeamNameMap.of(Map.of(
                "Man City", "Манчестер Сити",
                "Manchester City FC", "Манчестер Сити"
        ), true);
        assertEquals("Манчестер Сити", map.display("Man City"));
        assertEquals("Манчестер Сити", map.display("Manchester City FC"));
    }
}
