package com.example.predictor.release;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Ссылки на скачивание apk для браузера.
 *
 * Внутри приложения файл забирается по обычному входу, но ссылку, открытую в браузере или
 * присланную другу, никакой заголовок авторизации не сопровождает. Поэтому на каждую ссылку
 * выдаётся случайный ключ, который живёт сутки: достаточно, чтобы скачать, и мало, чтобы
 * ссылка гуляла по интернету вечно.
 *
 * Ключи лежат в памяти: после перезапуска сервера старые ссылки перестают работать, и это
 * не беда — новая берётся одним нажатием.
 */
@Component
public class DownloadLinks {

    /** Сколько живёт выданная ссылка. */
    private static final Duration LIFETIME = Duration.ofHours(24);

    /** Больше ключей одновременно не нужно: это семья и друзья, а не магазин приложений. */
    private static final int MAX_KEYS = 500;

    /** Выданная ссылка: ключ и срок, до которого она работает. */
    public record Link(String token, Instant expiresAt) {
    }

    private final Map<String, Instant> issued = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Новый ключ на сутки. */
    public Link issue() {
        forget();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant until = Instant.now().plus(LIFETIME);
        issued.put(token, until);
        return new Link(token, until);
    }

    /** Работает ли ещё эта ссылка. */
    public boolean valid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        forget();
        Instant until = issued.get(token);
        return until != null && until.isAfter(Instant.now());
    }

    /** Убирает просроченные ключи, а если их слишком много — самые старые. */
    private void forget() {
        Instant now = Instant.now();
        issued.values().removeIf(until -> !until.isAfter(now));
        while (issued.size() > MAX_KEYS) {
            Iterator<Map.Entry<String, Instant>> it = issued.entrySet().iterator();
            Map.Entry<String, Instant> oldest = null;
            while (it.hasNext()) {
                Map.Entry<String, Instant> e = it.next();
                if (oldest == null || e.getValue().isBefore(oldest.getValue())) {
                    oldest = e;
                }
            }
            if (oldest == null) {
                return;
            }
            issued.remove(oldest.getKey());
        }
    }
}
