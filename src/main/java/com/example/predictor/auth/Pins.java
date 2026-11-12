package com.example.predictor.auth;

/**
 * PIN-код для входа: от 4 до 8 цифр. Вводится один раз на телефоне, дальше работает
 * сохранённый токен. Хранится только хеш.
 *
 * <p>Это не замена паролю, а защита от того, чтобы посторонний вошёл под чужим именем,
 * зная только логин. Поэтому самые очевидные комбинации не принимаются.
 */
public final class Pins {

    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 8;

    /** Столько неверных попыток подряд, и вход блокируется на время. */
    public static final int MAX_FAILS = 5;

    private Pins() {
    }

    /** Текст ошибки или null, если PIN годится. */
    public static String validate(String pin) {
        if (pin == null || pin.isBlank()) {
            return "Нужен PIN-код из " + MIN_LENGTH + "–" + MAX_LENGTH + " цифр";
        }
        String value = pin.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            return "PIN-код должен быть из " + MIN_LENGTH + "–" + MAX_LENGTH + " цифр";
        }
        for (char c : value.toCharArray()) {
            if (c < '0' || c > '9') {
                return "PIN-код состоит только из цифр";
            }
        }
        if (allSame(value)) {
            return "Такой PIN-код слишком простой: все цифры одинаковые";
        }
        if (isSequence(value)) {
            return "Такой PIN-код слишком простой: цифры идут подряд";
        }
        return null;
    }

    public static String normalize(String pin) {
        return pin == null ? "" : pin.trim();
    }

    private static boolean allSame(String value) {
        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) != value.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    /** 1234 и 4321 одинаково плохи. */
    private static boolean isSequence(String value) {
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < value.length(); i++) {
            int step = value.charAt(i) - value.charAt(i - 1);
            if (step != 1) {
                up = false;
            }
            if (step != -1) {
                down = false;
            }
        }
        return up || down;
    }
}
