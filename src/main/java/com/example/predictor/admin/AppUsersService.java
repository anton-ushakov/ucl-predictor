package com.example.predictor.admin;

import com.example.predictor.common.AdminAccess;
import com.example.predictor.domain.AppUser;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.AuthTokenRepository;
import com.example.predictor.repository.DeviceTokenRepository;
import com.example.predictor.repository.GroupMemberRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Участники приложения глазами владельца сервера.
 *
 * Здесь закрывают доступ: человек не войдёт, не заведёт группу и не получит ни одного
 * оповещения. Запись о нём остаётся — прогнозы, итоги и долги прошлых туров не трогаем,
 * они касаются и остальных, — а доступ можно открыть обратно.
 */
@Service
public class AppUsersService {

    /** blocked — доступ закрыт; owner — владеет хотя бы одной группой. */
    public record UserDto(Long id, String username, String badge, boolean admin, boolean blocked,
                          String reason, Instant blockedAt, Instant createdAt, boolean owner,
                          boolean me) {
    }

    public record UsersView(List<UserDto> users, int blocked, String note) {
    }

    private final AdminAccess admins;
    private final AppUserRepository users;
    private final AuthTokenRepository tokens;
    private final DeviceTokenRepository devices;
    private final GroupMemberRepository members;

    public AppUsersService(AdminAccess admins, AppUserRepository users, AuthTokenRepository tokens,
                           DeviceTokenRepository devices, GroupMemberRepository members) {
        this.admins = admins;
        this.users = users;
        this.tokens = tokens;
        this.devices = devices;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public UsersView list(AppUser me, String query) {
        admins.require(me);
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<UserDto> list = new ArrayList<>();
        int blocked = 0;
        for (AppUser user : users.findAll()) {
            if (user.isBlocked()) {
                blocked++;
            }
            if (!needle.isEmpty() && !user.getUsername().toLowerCase().contains(needle)) {
                continue;
            }
            list.add(toDto(user, me));
        }
        // закрытые сверху: с ними и разбираются, остальные по именам
        list.sort(Comparator.comparing(UserDto::blocked).reversed()
                .thenComparing(UserDto::username, String.CASE_INSENSITIVE_ORDER));
        return new UsersView(list, blocked,
                "Закрытый доступ — это не удаление: прогнозы, итоги и долги прошлых туров "
                        + "остаются на месте, а человек просто не может войти.");
    }

    @Transactional
    public UserDto block(AppUser me, Long userId, String reason) {
        admins.require(me);
        AppUser user = require(userId);
        if (user.getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Себе доступ не закрывают: открывать будет некому");
        }
        if (admins.isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Это владелец приложения: его доступ меняется настройками сервера");
        }
        String clean = reason == null ? null : reason.trim();
        if (clean != null && clean.length() > 200) {
            clean = clean.substring(0, 200);
        }
        user.block(me.getId(), clean == null || clean.isEmpty() ? null : clean, Instant.now());
        users.save(user);
        // сеансы и телефоны: чтобы не осталось ни открытого приложения, ни оповещений
        tokens.deleteAllOfUser(user.getId());
        devices.deleteByUserId(user.getId());
        return toDto(user, me);
    }

    @Transactional
    public UserDto unblock(AppUser me, Long userId) {
        admins.require(me);
        AppUser user = require(userId);
        user.unblock();
        users.save(user);
        return toDto(user, me);
    }

    private AppUser require(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Участник не найден"));
    }

    private UserDto toDto(AppUser user, AppUser me) {
        return new UserDto(user.getId(), user.getUsername(), user.getBadge(), admins.isAdmin(user),
                user.isBlocked(), user.getBlockReason(), user.getBlockedAt(), user.getCreatedAt(),
                members.ownsAnyGroup(user.getId()), user.getId().equals(me.getId()));
    }
}
