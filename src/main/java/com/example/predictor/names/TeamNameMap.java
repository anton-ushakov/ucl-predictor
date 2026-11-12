package com.example.predictor.names;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Словарь русских названий команд. Ключ — оригинальное название без учёта регистра
 * и лишних пробелов, чтобы «Man City» и «man city» считались одной командой.
 */
public final class TeamNameMap {

    /** Пустой словарь: все названия показываются как есть. */
    public static final TeamNameMap EMPTY = new TeamNameMap(Map.of(), false);

    private final Map<String, String> russian;
    private final boolean useRussian;

    private TeamNameMap(Map<String, String> russian, boolean useRussian) {
        this.russian = russian;
        this.useRussian = useRussian;
    }

    /** Приводит название к виду ключа: без краевых пробелов и в нижнем регистре. */
    public static String key(String original) {
        return original == null ? "" : original.trim().toLowerCase(Locale.ROOT);
    }

    /** Собирает словарь из пар «оригинал → русское название». Пустые переводы пропускаются. */
    public static TeamNameMap of(Map<String, String> pairs, boolean useRussian) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, String> e : pairs.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            map.put(key(e.getKey()), e.getValue().trim());
        }
        return new TeamNameMap(map, useRussian);
    }

    /**
     * Название для показа участнику: русское, если группа их показывает и перевод есть,
     * иначе оригинальное.
     */
    public String display(String original) {
        if (!useRussian || original == null) {
            return original;
        }
        String name = russian.get(key(original));
        return name == null ? original : name;
    }

    /** Есть ли перевод (независимо от настройки группы). */
    public boolean has(String original) {
        return russian.containsKey(key(original));
    }

    /** Русское название или null, если его нет. */
    public String russianOf(String original) {
        return russian.get(key(original));
    }

    public int size() {
        return russian.size();
    }
}
