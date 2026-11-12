package com.example.predictor.domain;

/**
 * Какой счёт матча считается в группе. Основное время (90 минут) считается всегда.
 *
 * @param extraTime  прибавлять голы добавочного времени (до 120 минут)
 * @param penalties  прибавлять голы серии пенальти (например, 1:1 и серия 6:5 дают 7:6)
 */
public record ScoreBasis(boolean extraTime, boolean penalties) {

    public static final ScoreBasis REGULAR_TIME = new ScoreBasis(false, false);

    public String describe() {
        if (extraTime && penalties) {
            return "основное время + добавочное время + серия пенальти";
        }
        if (extraTime) {
            return "основное время + добавочное время";
        }
        if (penalties) {
            return "основное время + серия пенальти";
        }
        return "основное время (90 минут)";
    }
}
