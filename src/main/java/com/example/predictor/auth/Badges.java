package com.example.predictor.auth;

import java.util.List;
import java.util.Set;

/**
 * Значки участников: маленькая картинка перед именем, чтобы своих было видно сразу.
 * Набор задан здесь, а не вводится свободно: иначе в именах появится что угодно.
 */
public final class Badges {

    /** Порядок важен: в таком виде значки и показываются при выборе. */
    public static final List<String> ALL = List.of(
            "⚽", "🏆", "🥇", "🔥", "⭐", "💥",
            "🦁", "🐻", "🐺", "🦊", "🦅", "🐬",
            "🌯", "🍕", "☕", "🍀", "🎯", "🎲",
            "😎", "🤓", "👑", "🚀", "⚡", "🧊"
    );

    private static final Set<String> ALLOWED = Set.copyOf(ALL);

    private Badges() {
    }

    /** Приводит значок к хранимому виду: null означает «без значка». */
    public static String normalize(String badge) {
        if (badge == null) {
            return null;
        }
        String trimmed = badge.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isAllowed(String badge) {
        return badge == null || ALLOWED.contains(badge);
    }
}
