package com.example.predictor.auth;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Код восстановления доступа: выдаётся один раз при регистрации и показывается пользователю.
 * С ним можно задать новый пароль, не помня старого. Хранится только его хеш, как и у пароля.
 */
public final class RecoveryCodes {

    /** Без похожих друг на друга символов: нет I, O, 0 и 1, чтобы код было легко переписать. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    /** Код вида K7M2-QX9P-4RTB. */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 2);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Приводит введённый код к единому виду: убирает дефисы, пробелы и прочие лишние знаки,
     * делает буквы заглавными. Символов I, O, 0 и 1 в коде нет, поэтому исправлять нечего:
     * если такой символ введён, код просто не совпадёт.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (char c : raw.trim().toUpperCase(Locale.ROOT).toCharArray()) {
            if (ALPHABET.indexOf(c) >= 0) {
                result.append(c);
            }
        }
        return result.toString();
    }
}
