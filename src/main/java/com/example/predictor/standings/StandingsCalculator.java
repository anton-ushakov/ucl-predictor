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
import java.util.Set;

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
        return calculate(participants, matches, guesses, tiebreakers, scoring, Map.of());
    }

    /**
     * То же, но с джокерами: userId → matchId, баллы за этот матч считаются вдвойне.
     * Дополнительные показатели (точные счета, ошибка по голам) джокер не меняет:
     * он про риск в баллах, а не про меткость.
     */
    public static List<Row> calculate(Collection<Long> participants, List<FinishedMatch> matches,
                                      Collection<Guess> guesses, List<Tiebreaker> tiebreakers,
                                      Scoring scoring, Map<Long, Long> jokers) {
        return calculate(participants, matches, guesses, tiebreakers, scoring, jokers, Set.of());
    }

    /** То же, но часть участников играет вне конкурса: шаурма их не касается. */
    public static List<Row> calculate(Collection<Long> participants, List<FinishedMatch> matches,
                                      Collection<Guess> guesses, List<Tiebreaker> tiebreakers,
                                      Scoring scoring, Map<Long, Long> jokers,
                                      Set<Long> outOfCompetition) {
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
                boolean joker = Long.valueOf(m.matchId()).equals(jokers.get(userId));
                points += scoring.points(h) * (joker ? 2 : 1);
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
        return rank(rows, tiebreakers, outOfCompetition);
    }

    /**
     * Сортирует строки, расставляет места и отмечает победителей и проигравших.
     * Места "спортивные": 1, 1, 3. Победители: все, кто на 1 месте. Проигравшие: все, кто на последнем.
     * Если все участники делят одно место, победителей и проигравших нет.
     */
    public static List<Row> rank(List<Row> rows, List<Tiebreaker> tiebreakers) {
        return rank(rows, tiebreakers, Set.of());
    }

    /**
     * То же, но часть участников играет вне конкурса.
     *
     * Место и баллы у них считаются как у всех — они в таблице и в зачёте сезона. А вот
     * победителя и проигравшего ищем только среди тех, кто играет на шаурму: иначе гость,
     * занявший первое место, забирал бы чужую шаурму, а последнее — платил бы за тур, в
     * котором не участвовал.
     */
    public static List<Row> rank(List<Row> rows, List<Tiebreaker> tiebreakers,
                                 Set<Long> outOfCompetition) {
        Comparator<Row> cmp = comparator(tiebreakers);
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(cmp.thenComparingLong(Row::userId));

        int[] places = new int[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            boolean sameAsPrevious = i > 0 && cmp.compare(sorted.get(i - 1), sorted.get(i)) == 0;
            places[i] = sameAsPrevious ? places[i - 1] : i + 1;
        }

        // лучшее и худшее место среди тех, кто играет на шаурму
        int best = Integer.MAX_VALUE;
        int worst = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (outOfCompetition.contains(sorted.get(i).userId())) {
                continue;
            }
            best = Math.min(best, places[i]);
            worst = Math.max(worst, places[i]);
        }
        // все поделили одно место — ни победителей, ни проигравших
        boolean decided = worst > best;

        List<Row> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Row r = sorted.get(i);
            boolean playing = !outOfCompetition.contains(r.userId());
            result.add(new Row(r.userId(), r.points(), r.exactScores(), r.outcomeHits(), r.goalDiffHits(),
                    r.goalError(), r.submittedAt(), places[i],
                    playing && decided && places[i] == best,
                    playing && decided && places[i] == worst));
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
