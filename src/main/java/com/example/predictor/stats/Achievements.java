package com.example.predictor.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Достижения участника. Чистая логика без базы: на вход — итоги туров по порядку и счётчики
 * шаурмы, на выход — список с отметкой «получено» и прогрессом.
 *
 * <p>Достижения ничего не меняют в игре: это повод посмеяться и похвастаться, поэтому среди них
 * есть и обидные. Ничего не хранится отдельно — всё считается заново из итогов туров,
 * и значит, не может разойтись с историей.
 */
public final class Achievements {

    /** Итог одного тура участника, по порядку от раннего к позднему. */
    public record RoundLine(int points, int exactScores, boolean winner, boolean loser) {
    }

    /**
     * progress и target — для тех достижений, где виден путь («3 из 10»).
     * Если target = 0, показывать прогресс нечего.
     */
    public record Achievement(String code, String title, String detail, boolean earned,
                              int progress, int target) {
    }

    private Achievements() {
    }

    public static List<Achievement> of(List<RoundLine> rounds) {
        int totalExact = 0;
        int bestPoints = 0;
        int bestExact = 0;
        int wins = 0;
        int zeroRounds = 0;
        int bestWinStreak = 0;
        int winStreak = 0;
        int bestCleanStreak = 0;
        int cleanStreak = 0;
        for (RoundLine r : rounds) {
            totalExact += r.exactScores();
            bestPoints = Math.max(bestPoints, r.points());
            bestExact = Math.max(bestExact, r.exactScores());
            if (r.points() == 0) {
                zeroRounds++;
            }
            if (r.winner()) {
                wins++;
                winStreak++;
                bestWinStreak = Math.max(bestWinStreak, winStreak);
            } else {
                winStreak = 0;
            }
            if (r.loser()) {
                cleanStreak = 0;
            } else {
                cleanStreak++;
                bestCleanStreak = Math.max(bestCleanStreak, cleanStreak);
            }
        }

        List<Achievement> list = new ArrayList<>();
        list.add(make("FIRST_WIN", "Первая шаурма", "Выиграть тур", wins, 1));
        list.add(make("BACK_TO_BACK", "Две подряд", "Выиграть два тура подряд", bestWinStreak, 2));
        list.add(make("SNIPER", "Снайпер", "Три точных счёта в одном туре", bestExact, 3));
        list.add(make("TEN_IN_ROUND", "Десятка", "Набрать 10 баллов за тур", bestPoints, 10));
        list.add(make("EXACT_20", "Меткий", "Угадать 20 точных счетов", totalExact, 20));
        list.add(make("CLEAN_4", "Месяц без шаурмы", "Четыре тура подряд не последним",
                bestCleanStreak, 4));
        list.add(make("ZERO", "Бывает", "Пройти тур, не набрав ни одного балла", zeroRounds, 1));
        return list;
    }

    private static Achievement make(String code, String title, String detail, int progress,
                                    int target) {
        int capped = Math.min(progress, target);
        return new Achievement(code, title, detail, progress >= target, capped, target);
    }
}
