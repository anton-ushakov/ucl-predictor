package com.example.predictor.round;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.predictor.domain.Tiebreaker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Показатели у тех, кто сравнялся по баллам, и подпись под таблицей. */
class TiebreakNotesTest {

    private static final Instant WHEN = Instant.parse("2026-11-20T18:40:00Z");

    private static TiebreakNotes.Score score(long id, int points, int exact, int diff, int error) {
        return new TiebreakNotes.Score(id, points, exact, diff, error, WHEN);
    }

    @Test
    void noNotesWhenEveryoneHasDifferentPoints() {
        List<TiebreakNotes.Score> rows = List.of(score(1, 7, 1, 2, 5), score(2, 4, 0, 1, 9));
        List<Tiebreaker> order = List.of(Tiebreaker.EXACT_SCORES);

        assertTrue(TiebreakNotes.rowNotes(rows, order).isEmpty());
        assertNull(TiebreakNotes.note(rows, order));
    }

    @Test
    void tiedPlayersGetTheirIndicators() {
        List<TiebreakNotes.Score> rows = List.of(
                score(1, 5, 2, 3, 7), score(2, 5, 1, 2, 4), score(3, 9, 0, 0, 12));
        List<Tiebreaker> order = List.of(Tiebreaker.EXACT_SCORES, Tiebreaker.GOAL_ERROR);

        Map<Long, String> notes = TiebreakNotes.rowNotes(rows, order);
        assertEquals(2, notes.size());
        assertEquals("точных счетов 2 · ошибка по голам 7", notes.get(1L));
        assertEquals("точных счетов 1 · ошибка по голам 4", notes.get(2L));
        // у того, с кем никто не сравнялся, подписи нет: его место никто не оспаривает
        assertNull(notes.get(3L));
    }

    @Test
    void noteSaysWhichIndicatorsDecided() {
        List<TiebreakNotes.Score> rows = List.of(score(1, 5, 2, 3, 7), score(2, 5, 1, 2, 4));
        String note = TiebreakNotes.note(rows, List.of(Tiebreaker.EXACT_SCORES));

        assertTrue(note.contains("больше точных счетов"), note);
        assertTrue(note.contains("Ими и решилось"), note);
    }

    /** Ровно тот случай, из-за которого в туре оказалось двое проигравших. */
    @Test
    void noteWarnsWhenIndicatorsMatchedToo() {
        List<TiebreakNotes.Score> rows = List.of(score(1, 5, 1, 2, 7), score(2, 5, 1, 3, 4));
        String note = TiebreakNotes.note(rows, List.of(Tiebreaker.EXACT_SCORES));

        assertTrue(note.contains("делят место"), note);
        assertTrue(note.contains("настройках группы"), note);
    }

    @Test
    void noteExplainsEmptyTiebreakerList() {
        List<TiebreakNotes.Score> rows = List.of(score(1, 5, 1, 2, 7), score(2, 5, 0, 1, 4));
        String note = TiebreakNotes.note(rows, List.of());

        assertTrue(note.contains("не выбраны"), note);
        assertTrue(TiebreakNotes.rowNotes(rows, List.of()).isEmpty());
    }

    @Test
    void missingPredictionIsNamedInsteadOfTime() {
        TiebreakNotes.Score silent = new TiebreakNotes.Score(9L, 0, 0, 0, 30, null);
        assertEquals("прогноза не было",
                TiebreakNotes.valueOf(Tiebreaker.EARLIEST_SUBMISSION, silent));
    }
}
