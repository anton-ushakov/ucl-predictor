package com.example.predictor.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Дополнительные показатели, которые разделяют участников с одинаковым числом баллов.
 * Применяются по порядку: если по первому показателю разницы нет, смотрим на второй и т. д.
 * Если список пуст, участники с равными баллами делят место.
 */
public enum Tiebreaker {

    EXACT_SCORES("Больше точно угаданных счетов"),
    GOAL_DIFF_HITS("Больше угаданных разниц мячей (например, прогноз 2:0 при счёте 3:1)"),
    GOAL_ERROR("Меньше суммарная ошибка по голам (пропущенный матч считается как 10 голов ошибки)"),
    EARLIEST_SUBMISSION("Раньше сохранён прогноз");

    private final String description;

    Tiebreaker(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Разбор строки вида "EXACT_SCORES,GOAL_ERROR". Пустая строка даёт пустой список. */
    public static List<Tiebreaker> parseList(String csv) {
        List<Tiebreaker> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String part : csv.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                result.add(Tiebreaker.valueOf(name));
            }
        }
        return result;
    }

    public static String toCsv(List<Tiebreaker> list) {
        StringBuilder sb = new StringBuilder();
        for (Tiebreaker t : list) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.name());
        }
        return sb.toString();
    }
}
