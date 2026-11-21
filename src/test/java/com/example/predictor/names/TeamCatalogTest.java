package com.example.predictor.names;

import com.example.predictor.domain.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Эмблема команды: своя картинка или флаг, собранный по коду страны. */
class TeamCatalogTest {

    private final TeamCatalogService catalog =
            new TeamCatalogService(null, null, null, null, null, null,
                    "https://flagcdn.com/w160/{code}.png");

    @Test
    void ownCrestWins() {
        Team team = new Team(Team.Kind.CLUB, "Марсель", "https://example.org/om.png", null, null);
        assertEquals("https://example.org/om.png", catalog.crestOf(team));
    }

    @Test
    void flagIsBuiltFromCountryCode() {
        Team team = new Team(Team.Kind.NATIONAL, "Россия", null, "RU", null);
        assertEquals("https://flagcdn.com/w160/ru.png", catalog.crestOf(team));
    }

    @Test
    void footballNationsOfBritainHaveTheirOwnFlags() {
        Team team = new Team(Team.Kind.NATIONAL, "Англия", null, "gb-eng", null);
        assertEquals("https://flagcdn.com/w160/gb-eng.png", catalog.crestOf(team));
    }

    @Test
    void withoutCrestAndCodeThereIsNoPicture() {
        assertNull(catalog.crestOf(new Team(Team.Kind.CLUB, "Дворовая команда", null, null, null)));
    }
}
