package com.example.predictor.push;

/**
 * Минимальная работа с JSON для общения с Firebase.
 *
 * Готовой библиотеки здесь нет намеренно: нужно вытащить четыре поля из файла ключа
 * и одно из ответа, да собрать небольшое сообщение. Ради этого тянуть зависимость
 * и привязываться к её версии не стоит.
 */
public final class Json {

    private Json() {
    }

    /** Готовит строку к вставке в JSON: кавычки, переводы строк и прочее. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Значение строкового поля верхнего уровня. null, если поля нет или оно не строка.
     * Экранированные последовательности разворачиваются обратно: в ключе Firebase
     * переводы строк записаны как \n, и без этого ключ не прочитается.
     */
    public static String stringField(String json, String name) {
        if (json == null || name == null) {
            return null;
        }
        String needle = "\"" + name + "\"";
        int at = json.indexOf(needle);
        while (at >= 0) {
            int i = at + needle.length();
            i = skipSpaces(json, i);
            if (i < json.length() && json.charAt(i) == ':') {
                i = skipSpaces(json, i + 1);
                if (i < json.length() && json.charAt(i) == '"') {
                    return readString(json, i + 1);
                }
                return null; // поле есть, но значение не строка
            }
            at = json.indexOf(needle, at + 1);
        }
        return null;
    }

    private static int skipSpaces(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String readString(String json, int from) {
        StringBuilder out = new StringBuilder();
        int i = from;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
            i++;
        }
        return null; // строка не закрыта
    }
}
