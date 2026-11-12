package com.example.predictor.scoring;

/**
 * Начисление баллов за прогноз. Значения задаются в настройках группы и замораживаются на тур.
 *
 * <p>Баллы не суммируются: за матч начисляется ровно одна величина, самая выгодная из подходящих.
 * По умолчанию действуют привычные правила: точный счёт 3 балла, угаданный исход 1 балл,
 * за угаданную разницу мячей отдельных баллов нет.
 */
public record Scoring(int exactScorePoints, int goalDiffPoints, int outcomePoints) {

    /** Правила, с которых всё начиналось: 3 за точный счёт, 1 за исход. */
    public static final Scoring DEFAULT = new Scoring(3, 0, 1);

    /** Что угадал участник. */
    public enum Hit {
        /** Точный счёт. */
        EXACT,
        /** Разницу мячей и исход, но не счёт (например, 2:1 вместо 3:2). */
        GOAL_DIFF,
        /** Только исход. */
        OUTCOME,
        /** Ничего. */
        MISS
    }

    public Scoring {
        if (exactScorePoints < 0 || goalDiffPoints < 0 || outcomePoints < 0) {
            throw new IllegalArgumentException("Баллы не могут быть отрицательными");
        }
    }

    public static Hit hit(int predictedHome, int predictedAway, int actualHome, int actualAway) {
        if (predictedHome == actualHome && predictedAway == actualAway) {
            return Hit.EXACT;
        }
        if (outcome(predictedHome, predictedAway) != outcome(actualHome, actualAway)) {
            return Hit.MISS;
        }
        return predictedHome - predictedAway == actualHome - actualAway ? Hit.GOAL_DIFF : Hit.OUTCOME;
    }

    /**
     * Начисляет лучшее из подходящего. Точный счёт — это заодно и угаданная разница, и угаданный
     * исход, поэтому за него не может достаться меньше, чем за них. Так настройки нельзя задать
     * так, чтобы более точный прогноз приносил меньше баллов.
     */
    public int points(Hit hit) {
        return switch (hit) {
            case EXACT -> Math.max(exactScorePoints, Math.max(goalDiffPoints, outcomePoints));
            case GOAL_DIFF -> Math.max(goalDiffPoints, outcomePoints);
            case OUTCOME -> outcomePoints;
            case MISS -> 0;
        };
    }

    public int points(int predictedHome, int predictedAway, int actualHome, int actualAway) {
        return points(hit(predictedHome, predictedAway, actualHome, actualAway));
    }

    /** Понятное описание для экрана настроек и для истории. */
    public String describe() {
        StringBuilder text = new StringBuilder("Точный счёт: " + exactScorePoints);
        if (goalDiffPoints > 0) {
            text.append(", угаданная разница мячей: ").append(goalDiffPoints);
        }
        text.append(", угаданный исход: ").append(outcomePoints);
        return text.toString();
    }

    /** -1: победили гости, 0: ничья, 1: победили хозяева. */
    private static int outcome(int home, int away) {
        return Integer.compare(home, away);
    }
}
