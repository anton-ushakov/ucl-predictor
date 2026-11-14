package com.example.predictor.common;

import com.example.predictor.domain.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Владелец приложения — тот, кто держит сервер. Он один рассылает объявления всем участникам.
 *
 * Имена задаются свойством app.admin.usernames (через запятую) или переменной окружения
 * ADMIN_USERNAMES, рядом с паролем базы. В самой базе отметки нет: так проще и прозрачнее,
 * а список меняется перезапуском сервера.
 */
@Component
public class AdminAccess {

    private final Set<String> admins = new LinkedHashSet<>();

    public AdminAccess(@Value("${app.admin.usernames:${ADMIN_USERNAMES:}}") String usernames) {
        if (usernames != null && !usernames.isBlank()) {
            Arrays.stream(usernames.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(admins::add);
        }
    }

    public boolean isAdmin(AppUser user) {
        return user != null && user.getUsername() != null
                && admins.contains(user.getUsername().trim().toLowerCase(Locale.ROOT));
    }

    public void require(AppUser user) {
        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Объявления рассылает владелец приложения");
        }
    }

    /** Настроен ли вообще хоть один владелец приложения. */
    public boolean configured() {
        return !admins.isEmpty();
    }
}
