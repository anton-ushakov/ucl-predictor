package com.example.predictor.standings;

import com.example.predictor.domain.Tiebreaker;
import com.example.predictor.scoring.Scoring;
import com.example.predictor.scoring.Scoring.Hit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Подсчёт таблицы тура: баллы, дополнительные показатели, места, победители и проигравшие.
 * Чистая логика без базы данных и Spring, поэтому легко проверяется тестами.
 */
public final class StandingsCalculator {

    /** Ошибка по голам за пропущенный (не спрогнозированный) матч. */
    public static final int MISSED_PREDICTION_ERROR = 10;



    /** Сыгранный матч. */
    public record FinishedMatch(long matchId, int homeScore, int awayScore) {
    }

    /** Прогноз участника. */
    public record Guess(long userId, long matchId, int homeGoals, int awayGoals, Instant savedAt) {
    }

    /**
     * Строка таблицы.
     *
     * @param outcomeHits  матчи, где угадан только исход (1 балл)
     * @param goalDiffHits матчи, где угадана разница мячей (включая точный счёт)
     * @param goalError    сумма |прогноз - факт| по голам обеих команд
     * @param submittedAt  время последнего сохранённого прогноза (null, если прогнозов нет)
     */
    public record Row(long userId, int points, int exactScores, int outcomeHits, int goalDiffHits,
                      int goalError, Instant submittedAt, int place, boolean winner, boolean loser) {
    }

    private StandingsCalculator() {
    }

    public static List<Row> calculate(Collection<Long> participants, List<FinishedMatch> matches,
                                      Collection<Guess> guesses, List<Tiebreaker> tiebreakers,
                                      Scoring scoring) {
        Map<Long, Map<Long, Guess>> byUser = new HashMap<>();
        for (Guess g : guesses) {
            byUser.computeIfAbsent(g.userId(), k -> new HashMap<>()).put(g.matchId(), g);
        }

        List<Row> rows = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(participants)) {
            Map<Long, Guess> mine = byUser.getOrDefault(userId, Map.of());
            int points = 0;
            int exact = 0;
            int outcome = 0;
            int diff = 0;
            int error = 0;
            Instant last = null;
            for (FinishedMatch m : matches) {
                Guess g = mine.get(m.matchId());
                if (g == null) {
                    error += MISSED_PREDICTION_ERROR;
                    continue;
                }
                Hit h = Scoring.hit(g.homeGoals(), g.awayGoals(), m.homeScore(), m.awayScore());
                points += scoring.points(h);
                if (h == Hit.EXACT) {
                    exact++;
                } else if (h != Hit.MISS) {
                    // исход угадан: и когда совпала только разница мячей, и когда только исход
                    outcome++;
                }
                if (h == Hit.EXACT || h == Hit.GOAL_DIFF) {
                    diff++;
                }
                error += Math.abs(g.homeGoals() - m.homeScore()) + Math.abs(g.awayGoals() - m.awayScore());
                if (last == null || g.savedAt().isAfter(last)) {
                    last = g.savedAt();
                }
            }
            rows.add(new Row(userId, points, exact, outcome, diff, error, last, 0, false, false));
        }
        return rank(rows, tiebreakers);
    }

    /**
     * Сортирует строки, расставляет места и отмечает победителей и проигравших.
     * Места "спортивные": 1, 1, 3. Победители: все, кто на 1 месте. Проигравшие: все, кто на последнем.
     * Если все участники делят одно место, победителей и проигравших нет.
     */
    public static List<Row> rank(List<Row> rows, List<Tiebreaker> tiebreakers) {
        Comparator<Row> cmp = comparator(tiebreakers);
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(cmp.thenComparingLong(Row::userId));

        int[] places = new int[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            boolean sameAsPrevious = i > 0 && cmp.compare(sorted.get(i - 1), sorted.get(i)) == 0;
            places[i] = sameAsPrevious ? places[i - 1] : i + 1;
        }
        int worstPlace = sorted.isEmpty() ? 0 : places[places.length - 1];
        boolean decided = worstPlace > 1;

        List<Row> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Row r = sorted.get(i);
            result.add(new Row(r.userId(), r.points(), r.exactScores(), r.outcomeHits(), r.goalDiffHits(),
                    r.goalError(), r.submittedAt(), places[i],
                    decided && places[i] == 1, decided && places[i] == worstPlace));
        }
        return result;
    }

    private static Comparator<Row> comparator(List<Tiebreaker> tiebreakers) {
        Comparator<Row> c = (a, b) -> Integer.compare(b.points(), a.points());
        for (Tiebreaker t : tiebreakers) {
            Comparator<Row> next = switch (t) {
                case EXACT_SCORES -> (a, b) -> Integer.compare(b.exactScores(), a.exactScores());
                case GOAL_DIFF_HITS -> (a, b) -> Integer.compare(b.goalDiffHits(), a.goalDiffHits());
                case GOAL_ERROR -> (a, b) -> Integer.compare(a.goalError(), b.goalError());
                case EARLIEST_SUBMISSION -> (a, b) -> compareSubmitted(a.submittedAt(), b.submittedAt());
            };
            c = c.thenComparing(next);
        }
        return c;
    }

    /** Раньше сохранил: лучше. Тот, кто не сохранял вовсе, в конце. */
    private static int compareSubmitted(Instant a, Instant b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }
}
