package com.example.predictor.standings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.predictor.domain.Tiebreaker;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Участие вне конкурса: баллы и место есть, шаурмы нет. */
class ExhibitionRankingTest {

    private static final Instant WHEN = Instant.parse("2026-11-20T18:00:00Z");
    private static final List<Tiebreaker> ORDER = List.of(Tiebreaker.EXACT_SCORES);

    private static StandingsCalculator.Row row(long userId, int points, int exact) {
        return new StandingsCalculator.Row(userId, points, exact, 0, 0, 0, WHEN, 0, false, false);
    }

    private static StandingsCalculator.Row find(List<StandingsCalculator.Row> rows, long userId) {
        return rows.stream().filter(r -> r.userId() == userId).findFirst().orElseThrow();
    }

    @Test
    void guestOnTopDoesNotTakeTheShawarma() {
        // гость набрал больше всех: место первое, но победитель — лучший из играющих
        List<StandingsCalculator.Row> ranked = StandingsCalculator.rank(
                List.of(row(1, 9, 3), row(2, 7, 2), row(3, 4, 1)), ORDER, Set.of(1L));

        assertEquals(1, find(ranked, 1).place());
        assertFalse(find(ranked, 1).winner(), "гость не получает шаурму");
        assertTrue(find(ranked, 2).winner(), "выигрывает лучший из играющих");
        assertTrue(find(ranked, 3).loser());
    }

    @Test
    void guestAtTheBottomDoesNotBuyIt() {
        List<StandingsCalculator.Row> ranked = StandingsCalculator.rank(
                List.of(row(1, 9, 3), row(2, 7, 2), row(3, 1, 0)), ORDER, Set.of(3L));

        assertEquals(3, find(ranked, 3).place());
        assertFalse(find(ranked, 3).loser(), "гость не покупает шаурму");
        assertTrue(find(ranked, 2).loser(), "платит последний из играющих");
        assertTrue(find(ranked, 1).winner());
    }

    @Test
    void pointsStayWithTheGuest() {
        // баллы и показатели считаются как у всех: они идут в зачёт сезона
        List<StandingsCalculator.Row> ranked = StandingsCalculator.rank(
                List.of(row(1, 9, 3), row(2, 7, 2)), ORDER, Set.of(1L));

        assertEquals(9, find(ranked, 1).points());
        assertEquals(3, find(ranked, 1).exactScores());
    }

    @Test
    void onlyOnePlayerLeftMeansNobodyWinsOrPays() {
        // один играет, остальные вне конкурса: спорить не с кем, шаурмы нет
        List<StandingsCalculator.Row> ranked = StandingsCalculator.rank(
                List.of(row(1, 9, 3), row(2, 7, 2)), ORDER, Set.of(2L));

        assertFalse(find(ranked, 1).winner());
        assertFalse(find(ranked, 1).loser());
    }

    @Test
    void withoutGuestsNothingChanges() {
        List<StandingsCalculator.Row> ranked = StandingsCalculator.rank(
                List.of(row(1, 9, 3), row(2, 7, 2), row(3, 4, 1)), ORDER, Set.of());

        assertTrue(find(ranked, 1).winner());
        assertTrue(find(ranked, 3).loser());
        assertFalse(find(ranked, 2).winner());
        assertFalse(find(ranked, 2).loser());
    }
}
