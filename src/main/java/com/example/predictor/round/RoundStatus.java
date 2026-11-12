package com.example.predictor.round;

/** Состояние тура (недели) для группы. */
public enum RoundStatus {
    /** Ни один матч ещё не начался. */
    UPCOMING,
    /** Хотя бы один матч начался, итоги ещё не подведены. */
    IN_PROGRESS,
    /** Итоги подведены и сохранены в историю. */
    FINISHED,
    /** Все матчи сыграны, но в группе никто не делал прогнозов: подводить нечего. */
    NO_PREDICTIONS;

    /**
     * allMatchesOver: все матчи тура сыграны или отменены.
     * groupHasPredictions: хоть кто-то в группе сохранил прогноз на этот тур.
     */
    public static RoundStatus of(boolean finalized, boolean started, boolean allMatchesOver,
                                 boolean groupHasPredictions) {
        if (finalized) {
            return FINISHED;
        }
        if (allMatchesOver && !groupHasPredictions) {
            return NO_PREDICTIONS;
        }
        return started ? IN_PROGRESS : UPCOMING;
    }
}
