package com.example.predictor.round;

import com.example.predictor.domain.Tiebreaker;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Почему участники с равными баллами встали именно так — и почему иногда не встали вовсе.
 *
 * Баллы сходятся часто, особенно в небольшом туре. Тогда порядок решают дополнительные
 * показатели группы, и если в списке один показатель, а он тоже совпал, место остаётся
 * общим: победителей или проигравших оказывается двое. Снаружи это выглядит поломкой,
 * поэтому показатели видно прямо в таблице — у тех, кого они касаются.
 */
public final class TiebreakNotes {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
                    .withZone(MOSCOW);

    /** Участник в том виде, в каком его различают показатели. */
    public record Score(Long userId, int points, int exactScores, int goalDiffHits, int goalError,
                        Instant submittedAt) {
    }

    private TiebreakNotes() {
    }

    /**
     * Строка показателей для каждого, кто сравнялся с кем-то по баллам. У остальных строки
     * нет: их место ничем не оспаривается, и лишняя подпись только мешала бы читать.
     */
    public static Map<Long, String> rowNotes(List<Score> rows, List<Tiebreaker> order) {
        Map<Long, String> notes = new HashMap<>();
        if (order.isEmpty()) {
            return notes;
        }
        for (List<Score> tied : byPoints(rows).values()) {
            if (tied.size() < 2) {
                continue;
            }
            for (Score s : tied) {
                List<String> parts = new ArrayList<>();
                for (Tiebreaker t : order) {
                    parts.add(valueOf(t, s));
                }
                notes.put(s.userId(), String.join(" · ", parts));
            }
        }
        return notes;
    }

    /**
     * Подпись под таблицей: по каким показателям разводили равные баллы и чем кончилось.
     * null — равных баллов в туре не было, объяснять нечего.
     */
    public static String note(List<Score> rows, List<Tiebreaker> order) {
        List<List<Score>> ties = new ArrayList<>();
        for (List<Score> tied : byPoints(rows).values()) {
            if (tied.size() >= 2) {
                ties.add(tied);
            }
        }
        if (ties.isEmpty()) {
            return null;
        }
        if (order.isEmpty()) {
            return "Баллы у части участников сошлись, а дополнительные показатели в группе "
                    + "не выбраны — поэтому место у них общее. Показатели задаются в настройках "
                    + "группы и действуют со следующего тура.";
        }

        StringBuilder sb = new StringBuilder("Равные баллы развели показатели: ");
        for (int i = 0; i < order.size(); i++) {
            sb.append(i == 0 ? "" : ", затем ").append(titleOf(order.get(i)));
        }
        sb.append(". ");

        int unresolved = 0;
        for (List<Score> tied : ties) {
            unresolved += Math.max(0, sameAll(tied, order) - 1);
        }
        if (unresolved > 0) {
            sb.append("У кого-то совпали и они — такие участники делят место, поэтому "
                    + "победителей или проигравших может оказаться двое. Чтобы это случалось "
                    + "реже, добавьте показатель в настройках группы: он подействует со "
                    + "следующего тура.");
        } else {
            sb.append("Ими и решилось, кто выше.");
        }
        return sb.toString();
    }

    /** Сколько участников делят место, потому что совпали все показатели. */
    private static int sameAll(List<Score> tied, List<Tiebreaker> order) {
        int most = 0;
        Map<String, Integer> seen = new HashMap<>();
        for (Score s : tied) {
            StringBuilder key = new StringBuilder();
            for (Tiebreaker t : order) {
                key.append(valueOf(t, s)).append('|');
            }
            int count = seen.merge(key.toString(), 1, Integer::sum);
            most = Math.max(most, count);
        }
        return most;
    }

    /** Участники, сгруппированные по числу баллов. */
    private static Map<Integer, List<Score>> byPoints(List<Score> rows) {
        Map<Integer, List<Score>> byPoints = new LinkedHashMap<>();
        for (Score s : rows) {
            byPoints.computeIfAbsent(s.points(), k -> new ArrayList<>()).add(s);
        }
        return byPoints;
    }

    /** «точных счетов 2», «ошибка по голам 7» — показатель со своим значением. */
    static String valueOf(Tiebreaker t, Score s) {
        return switch (t) {
            case EXACT_SCORES -> "точных счетов " + s.exactScores();
            case GOAL_DIFF_HITS -> "разниц мячей " + s.goalDiffHits();
            case GOAL_ERROR -> "ошибка по голам " + s.goalError();
            case EARLIEST_SUBMISSION -> s.submittedAt() == null
                    ? "прогноза не было"
                    : "прогноз " + TIME.format(s.submittedAt());
        };
    }

    /** Как показатель называется в подписи под таблицей. */
    static String titleOf(Tiebreaker t) {
        return switch (t) {
            case EXACT_SCORES -> "больше точных счетов";
            case GOAL_DIFF_HITS -> "больше угаданных разниц мячей";
            case GOAL_ERROR -> "меньше ошибка по голам";
            case EARLIEST_SUBMISSION -> "раньше сохранён прогноз";
        };
    }
}
