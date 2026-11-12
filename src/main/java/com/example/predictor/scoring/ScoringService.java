package com.example.predictor.scoring;

import org.springframework.stereotype.Service;

/**
 * Правила подсчёта очков:
 * <ul>
 *   <li>точно угаданный счёт: 3 балла;</li>
 *   <li>угадан только исход (победа хозяев, ничья, победа гостей): 1 балл;</li>
 *   <li>иначе: 0.</li>
 * </ul>
 * Баллы за счёт и за исход не суммируются: точный счёт даёт ровно 3.
 */
@Service
public class ScoringService {

    public static final int POINTS_EXACT_SCORE = 3;
    public static final int POINTS_OUTCOME = 1;

    public int points(int predictedHome, int predictedAway, int actualHome, int actualAway) {
        if (predictedHome == actualHome && predictedAway == actualAway) {
            return POINTS_EXACT_SCORE;
        }
        if (outcome(predictedHome, predictedAway) == outcome(actualHome, actualAway)) {
            return POINTS_OUTCOME;
        }
        return 0;
    }

    /** -1: победили гости, 0: ничья, 1: победили хозяева. */
    private int outcome(int home, int away) {
        return Integer.compare(home, away);
    }
}
