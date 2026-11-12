package com.example.predictor.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Текущий способ входа. Задаётся настройкой app.auth.mode (SIMPLE или PASSWORD),
 * по умолчанию SIMPLE. Выключенная половина отвечает понятной ошибкой, а не пропадает.
 */
@Component
public class AuthModeConfig {

    private final AuthMode mode;

    public AuthModeConfig(@Value("${app.auth.mode:SIMPLE}") AuthMode mode) {
        this.mode = mode;
    }

    public AuthMode mode() {
        return mode;
    }

    public boolean passwordsEnabled() {
        return mode == AuthMode.PASSWORD;
    }

    /** Приглашения и заявки работают в простом режиме, коды приглашения — в парольном. */
    public boolean requestsEnabled() {
        return mode == AuthMode.SIMPLE;
    }

    public void requirePasswords() {
        if (!passwordsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Вход по паролю выключен: приложение работает в простом режиме, вход по логину");
        }
    }

    /** Коды приглашения работают только в парольном режиме. */
    public void requireInviteCodes() {
        if (requestsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Коды приглашения выключены: в группу вступают по приглашению владельца "
                            + "или по заявке");
        }
    }

    public void requireRequests() {
        if (!requestsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Приглашения и заявки выключены: приложение работает в режиме с паролями, "
                            + "в группу вступают по коду приглашения");
        }
    }
}
