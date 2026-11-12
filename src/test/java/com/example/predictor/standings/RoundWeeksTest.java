package com.example.predictor.standings;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundWeeksTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Test
    void mondayStaysMonday() {
        assertEquals(LocalDate.of(2026, 11, 2), RoundWeeks.mondayOf(LocalDate.of(2026, 11, 2)));
    }

    @Test
    void sundayBelongsToThePreviousMonday() {
        assertEquals(LocalDate.of(2026, 11, 2), RoundWeeks.mondayOf(LocalDate.of(2026, 11, 8)));
    }

    @Test
    void lateTuesdayMatchStaysInItsWeek() {
        // Вторник 22:00 UTC = среда 01:00 по Москве, всё равно та же неделя
        Instant kickoff = Instant.parse("2026-11-03T22:00:00Z");
        assertEquals(LocalDate.of(2026, 11, 2), RoundWeeks.mondayOf(kickoff, MOSCOW));
    }

    @Test
    void sundayNightUtcCanBeMondayInMoscow() {
        // Воскресенье 22:30 UTC = понедельник 01:30 по Москве, это уже следующая неделя
        Instant kickoff = Instant.parse("2026-11-08T22:30:00Z");
        assertEquals(LocalDate.of(2026, 11, 9), RoundWeeks.mondayOf(kickoff, MOSCOW));
    }
}
