package com.example.predictor.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Отметка «счёт устоялся»: от неё отсчитывается пауза перед подведением итогов. Ждать
 * фиксированное время от начала матча нельзя — с добавочным временем и пенальти матч идёт
 * дольше трёх часов.
 */
class MatchSettledTest {

    private static final Instant KICKOFF = Instant.parse("2026-11-25T20:00:00Z");
    private static final Instant WHISTLE = Instant.parse("2026-11-25T21:50:00Z");
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 23);

    private static FootballMatch match() {
        return new FootballMatch(1L);
    }

    private static boolean finish(FootballMatch m, int home, int away, String duration) {
        return m.applyFeed("Спортинг", "МЮ", null, null, KICKOFF, WEEK, "LEAGUE_STAGE", 5,
                MatchStatus.FINISHED, home, away, null, null, null, null, duration);
    }

    @Test
    void unfinishedMatchHasNoMark() {
        FootballMatch m = match();
        m.applyFeed("Спортинг", "МЮ", null, null, KICKOFF, WEEK, "LEAGUE_STAGE", 5,
                MatchStatus.SCHEDULED, null, null, null, null, null, null, null);
        m.noteSettled(WHISTLE, false);

        assertNull(m.getSettledAt());
    }

    @Test
    void finishedMatchIsMarkedOnce() {
        FootballMatch m = match();
        finish(m, 2, 2, "REGULAR");
        m.noteSettled(WHISTLE, false);
        assertEquals(WHISTLE, m.getSettledAt());

        // следующая проверка источника ничего не поменяла: отметка стоит на месте
        boolean changed = finish(m, 2, 2, "REGULAR");
        m.noteSettled(WHISTLE.plusSeconds(600), changed);
        assertEquals(WHISTLE, m.getSettledAt());
    }

    @Test
    void correctedScoreStartsTheWaitAgain() {
        FootballMatch m = match();
        finish(m, 2, 3, "REGULAR");
        m.noteSettled(WHISTLE, false);

        // гол отменили по VAR: счёт поправили, значит ждём заново
        Instant later = WHISTLE.plusSeconds(900);
        boolean changed = finish(m, 2, 2, "REGULAR");
        assertTrue(changed, "правка счёта должна быть замечена");
        m.noteSettled(later, changed);

        assertEquals(later, m.getSettledAt());
    }

    @Test
    void extraTimeGoalsCountAsAChange() {
        FootballMatch m = match();
        finish(m, 1, 1, "REGULAR");
        m.noteSettled(WHISTLE, false);

        // источник досчитал добавочное время: для нас это другой счёт
        boolean changed = m.applyFeed("Спортинг", "МЮ", null, null, KICKOFF, WEEK, "SEMI_FINAL", 5,
                MatchStatus.FINISHED, 1, 1, 1, 0, null, null, "EXTRA_TIME");
        assertTrue(changed);
        m.noteSettled(WHISTLE.plusSeconds(1800), changed);

        assertEquals(WHISTLE.plusSeconds(1800), m.getSettledAt());
    }

    @Test
    void matchTakenBackToLiveLosesTheMark() {
        FootballMatch m = match();
        finish(m, 2, 2, "REGULAR");
        m.noteSettled(WHISTLE, false);

        // источник передумал: матч снова идёт
        m.applyFeed("Спортинг", "МЮ", null, null, KICKOFF, WEEK, "LEAGUE_STAGE", 5,
                MatchStatus.LIVE, 2, 2, null, null, null, null, null);
        m.noteSettled(WHISTLE.plusSeconds(60), false);

        assertNull(m.getSettledAt());
    }
}
