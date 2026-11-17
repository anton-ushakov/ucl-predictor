package com.example.predictor.round;

import java.util.Map;

/**
 * Названия этапов турнира по-русски.
 *
 * Источник отдаёт этап кодом: LEAGUE_STAGE, LAST_16, REGULAR_SEASON. Показывать такое
 * участникам незачем — это шум латиницей посреди русского экрана. Известные коды
 * переводим, незнакомые прячем, а этап, вписанный руками, оставляем как есть.
 */
public final class StageLabels {

    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("LEAGUE_STAGE", "Общий этап"),
            Map.entry("GROUP_STAGE", "Групповой этап"),
            Map.entry("PLAYOFFS", "Плей-офф"),
            Map.entry("PLAY_OFF_ROUND", "Раунд плей-офф"),
            Map.entry("PLAYOFF_ROUND", "Раунд плей-офф"),
            Map.entry("ROUND_OF_32", "1/16 финала"),
            Map.entry("LAST_32", "1/16 финала"),
            Map.entry("ROUND_OF_16", "1/8 финала"),
            Map.entry("LAST_16", "1/8 финала"),
            Map.entry("QUARTER_FINALS", "1/4 финала"),
            Map.entry("QUARTER_FINAL", "1/4 финала"),
            Map.entry("SEMI_FINALS", "1/2 финала"),
            Map.entry("SEMI_FINAL", "1/2 финала"),
            Map.entry("THIRD_PLACE", "Матч за третье место"),
            Map.entry("FINAL", "Финал"),
            Map.entry("PRELIMINARY_ROUND", "Квалификация"),
            Map.entry("PRELIMINARY_SEMI_FINALS", "Квалификация"),
            Map.entry("PRELIMINARY_FINAL", "Квалификация"),
            Map.entry("FIRST_QUALIFYING_ROUND", "Первый отборочный раунд"),
            Map.entry("SECOND_QUALIFYING_ROUND", "Второй отборочный раунд"),
            Map.entry("THIRD_QUALIFYING_ROUND", "Третий отборочный раунд"),
            Map.entry("QUALIFICATION", "Квалификация")
    );

    /** Этапы, которые не говорят ничего: обычный ход чемпионата. */
    private static final java.util.Set<String> HIDDEN =
            java.util.Set.of("REGULAR_SEASON", "LEAGUE", "NONE", "UNKNOWN");

    private StageLabels() {
    }

    /**
     * Человеческое название этапа или {@code null}, если показывать нечего.
     */
    public static String of(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        String code = stage.trim();
        String upper = code.toUpperCase(java.util.Locale.ROOT);
        String known = KNOWN.get(upper);
        if (known != null) {
            return known;
        }
        if (HIDDEN.contains(upper)) {
            return null;
        }
        // код источника, которого мы не знаем: латиница, цифры и подчёркивания — прячем
        if (code.matches("[A-Za-z0-9_\\- ]+") && code.equals(upper)) {
            return null;
        }
        return code;
    }
}
