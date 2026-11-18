package com.example.predictor.round;

import com.example.predictor.standings.RoundWeeks;
import java.time.LocalDate;

/**
 * Ключ тура.
 *
 * В неделе может быть несколько туров, а ключом всюду служит дата — так сложилось исторически
 * и так удобно: по ключу сразу видно и неделю, и порядок. Первый тур недели получает её
 * понедельник, второй вторник, третий среду. Больше семи туров в неделю не бывает: это игра
 * с друзьями, а не биржа.
 */
public final class RoundKeys {

    /** Семь дней — семь возможных туров в неделе. */
    public static final int MAX_PER_WEEK = 7;

    private RoundKeys() {
    }

    /** Ключ тура номер no (от 1) в неделе, начинающейся с этого понедельника. */
    public static LocalDate keyFor(LocalDate monday, int no) {
        if (no < 1 || no > MAX_PER_WEEK) {
            throw new IllegalArgumentException("Номер тура вне недели: " + no);
        }
        return RoundWeeks.mondayOf(monday).plusDays(no - 1L);
    }

    /** Номер тура в своей неделе: понедельник — первый, воскресенье — седьмой. */
    public static int noOf(LocalDate key) {
        return key.getDayOfWeek().getValue();
    }

    /** Неделя, к которой относится тур. */
    public static LocalDate weekOf(LocalDate key) {
        return RoundWeeks.mondayOf(key);
    }

    /** Первый тур недели: его ключ — понедельник. */
    public static boolean isFirst(LocalDate key) {
        return noOf(key) == 1;
    }
}
