package com.example.predictor.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.predictor.feed.TeamFormService.Played;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Форма по своим матчам: когда источник её не присылает, считаем сами. */
class TeamFormTest {

    @Test
    void winsDrawsAndLossesFromBothSides() {
        Map<String, String> form = TeamFormService.build(List.of("Спартак"), List.of(
                new Played("Спартак", "Зенит", 2, 1),
                new Played("ЦСКА", "Спартак", 3, 3),
                new Played("Спартак", "Динамо", 0, 1)));
        // свежий матч последним: проиграл, сыграл вничью, выиграл
        assertEquals("LDW", form.get("Спартак"));
    }

    @Test
    void onlyFiveLastMatches() {
        List<Played> matches = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            matches.add(new Played("Спартак", "Соперник " + i, 1, 0));
        }
        assertEquals("WWWWW", TeamFormService.build(List.of("Спартак"), matches).get("Спартак"));
    }

    @Test
    void matchWithoutScoreIsSkipped() {
        Map<String, String> form = TeamFormService.build(List.of("Спартак"), List.of(
                new Played("Спартак", "Зенит", null, null),
                new Played("Спартак", "Динамо", 2, 0)));
        assertEquals("W", form.get("Спартак"));
    }

    @Test
    void teamWithoutMatchesIsAbsent() {
        Map<String, String> form = TeamFormService.build(List.of("Арсенал"), List.of(
                new Played("Спартак", "Зенит", 1, 0)));
        assertNull(form.get("Арсенал"));
    }
}
