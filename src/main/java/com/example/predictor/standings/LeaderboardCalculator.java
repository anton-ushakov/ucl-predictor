package com.example.predictor.standings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Общий зачёт по завершённым турам: сумма баллов, затем больше точных счетов, затем больше побед
 * в турах. Если и по ним поровну, участники делят место. Чистая логика.
 */
public final class LeaderboardCalculator {

    /** Итог одного участника в одном завершённом туре. */
    public record ResultLine(long userId, int points, int exactScores, boolean winner, boolean loser) {
    }

    public record Row(long userId, int place, int rounds, int totalPoints, int exactScores,
                      int wins, int losses) {
    }

    private LeaderboardCalculator() {
    }

    public static List<Row> calculate(List<ResultLine> lines) {
        // rounds, points, exact, wins, losses
        Map<Long, int[]> acc = new LinkedHashMap<>();
        for (ResultLine l : lines) {
            int[] a = acc.computeIfAbsent(l.userId(), k -> new int[5]);
            a[0]++;
            a[1] += l.points();
            a[2] += l.exactScores();
            if (l.winner()) {
                a[3]++;
            }
            if (l.loser()) {
                a[4]++;
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : acc.entrySet()) {
            int[] a = e.getValue();
            rows.add(new Row(e.getKey(), 0, a[0], a[1], a[2], a[3], a[4]));
        }

        Comparator<Row> byScore = (x, y) -> {
            int c = Integer.compare(y.totalPoints(), x.totalPoints());
            if (c != 0) {
                return c;
            }
            c = Integer.compare(y.exactScores(), x.exactScores());
            if (c != 0) {
                return c;
            }
            return Integer.compare(y.wins(), x.wins());
        };
        rows.sort(byScore.thenComparingLong(Row::userId));

        List<Row> result = new ArrayList<>();
        int place = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i == 0 || byScore.compare(rows.get(i - 1), r) != 0) {
                place = i + 1;
            }
            result.add(new Row(r.userId(), place, r.rounds(), r.totalPoints(), r.exactScores(),
                    r.wins(), r.losses()));
        }
        return result;
    }
}
