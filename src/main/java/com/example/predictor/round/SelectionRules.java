package com.example.predictor.round;

import java.time.LocalDate;

/**
 * Правила отбора матчей в тур и ввода счёта ручного матча. Чистая логика без базы и сети,
 * чтобы её можно было проверить тестами.
 *
 * Каждый метод возвращает null, если всё в порядке, или причину отказа вместе с кодом ответа.
 */
public final class SelectionRules {

    /** Причина отказа: код ответа HTTP и текст для участника. */
    public record Problem(int status, String message) {
    }

    private SelectionRules() {
    }

    /**
     * Можно ли добавить матч в тур: он должен относиться к неделе, на которую ещё принимаются
     * прогнозы, и приём прогнозов на сам матч не должен быть закрыт.
     */
    public static Problem canAdd(String title, LocalDate matchWeek, LocalDate firstWeek,
                                 LocalDate lastWeek, boolean locked) {
        if (matchWeek.isBefore(firstWeek) || matchWeek.isAfter(lastWeek)) {
            return new Problem(409, "В тур можно добавлять матчи текущей и следующей недели, а матч "
                    + title + " относится к туру " + matchWeek);
        }
        if (locked) {
            return new Problem(409, "Приём прогнозов на матч " + title
                    + " уже закрыт, добавить его в тур нельзя");
        }
        return null;
    }

    /** Можно ли убрать матч из тура: нельзя, если на него уже есть прогнозы. */
    public static Problem canRemove(boolean inRound, boolean hasPredictions) {
        if (!inRound) {
            return new Problem(404, "Этого матча нет в туре");
        }
        if (hasPredictions) {
            return new Problem(409, "На этот матч уже есть прогнозы, убрать его из тура нельзя");
        }
        return null;
    }

    /** Проверяет время начала ручного матча: он должен попасть в тур, на который ещё можно играть. */
    public static Problem canPlaceManual(LocalDate matchWeek, LocalDate firstWeek, LocalDate lastWeek) {
        if (matchWeek.isBefore(firstWeek) || matchWeek.isAfter(lastWeek)) {
            return new Problem(409, "Матч можно добавить только на текущую или следующую неделю");
        }
        return null;
    }

    /** Проверяет команды ручного матча. */
    public static Problem checkTeams(String home, String away) {
        if (home == null || home.isBlank() || away == null || away.isBlank()) {
            return new Problem(400, "Укажите обе команды");
        }
        if (home.trim().equalsIgnoreCase(away.trim())) {
            return new Problem(400, "Хозяева и гости не могут быть одной командой");
        }
        return null;
    }

    /**
     * Проверяет счёт ручного матча: счёт периода указывается для обеих команд сразу,
     * а серия пенальти бывает только после добавочного времени. Счёт вводится после
     * закрытия приёма прогнозов, иначе владелец знал бы результат раньше остальных.
     */
    public static Problem checkManualScore(boolean locked, Integer extraHome, Integer extraAway,
                                           Integer penHome, Integer penAway) {
        if (!locked) {
            return new Problem(409,
                    "Приём прогнозов на этот матч ещё открыт: счёт можно ввести после его закрытия");
        }
        if ((extraHome == null) != (extraAway == null)) {
            return new Problem(400, "Счёт добавочного времени указывается для обеих команд");
        }
        if ((penHome == null) != (penAway == null)) {
            return new Problem(400, "Счёт серии пенальти указывается для обеих команд");
        }
        if (penHome != null && extraHome == null) {
            return new Problem(400, "Серия пенальти бывает после добавочного времени: укажите и его счёт");
        }
        return null;
    }
}
