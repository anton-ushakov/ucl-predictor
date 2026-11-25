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
 *
 * Имя можно поменять прямо в приложении, и тогда владелец перестаёт совпадать со списком —
 * права пропадают молча, а вместе с ними рассылка новой версии и заявки на регистрацию.
 * Поэтому в списке понимается и номер участника: «id:3» или «id:3,анна». Номер не меняется
 * никогда, так что переименование его не затронет. Номер виден на экране «Участники
 * приложения» и в базе: SELECT id, username FROM app_users.
 */
@Component
public class AdminAccess {

    private final Set<String> admins = new LinkedHashSet<>();
    private final Set<Long> adminIds = new LinkedHashSet<>();

    public AdminAccess(@Value("${app.admin.usernames:${ADMIN_USERNAMES:}}") String usernames) {
        if (usernames == null || usernames.isBlank()) {
            return;
        }
        for (String part : usernames.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.regionMatches(true, 0, "id:", 0, 3)) {
                try {
                    adminIds.add(Long.parseLong(entry.substring(3).trim()));
                } catch (NumberFormatException e) {
                    // «id:» без числа — просто имя, пусть ищется как имя
                    admins.add(entry.toLowerCase(Locale.ROOT));
                }
            } else {
                admins.add(entry.toLowerCase(Locale.ROOT));
            }
        }
    }

    /** Имена владельцев приложения из настроек сервера. */
    public Set<String> names() {
        return java.util.Collections.unmodifiableSet(admins);
    }

    /** Номера владельцев приложения, если их задали как «id:3». */
    public Set<Long> ids() {
        return java.util.Collections.unmodifiableSet(adminIds);
    }

    public boolean isAdmin(AppUser user) {
        if (user == null) {
            return false;
        }
        if (user.getId() != null && adminIds.contains(user.getId())) {
            return true;
        }
        return user.getUsername() != null
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
