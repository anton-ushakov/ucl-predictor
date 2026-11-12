package com.example.predictor.names;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.TeamName;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.TeamNameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeSet;

/**
 * Русские названия команд. Источник присылает их латиницей, а в приложении удобнее читать
 * по-русски. Словарь общий для всех групп и пополняется участниками: владелец группы выбирает
 * оригинальное название из списка команд, которые встречаются в матчах, и вводит русское.
 * Показывать русские названия или оригинальные — настройка группы.
 */
@Service
public class TeamNamesService {

    /** Одна строка словаря. inMatches=true: такая команда встречается в матчах. */
    public record TeamNameItem(String original, String russian, boolean inMatches) {
    }

    /** Экран словаря. canEdit=true: это владелец группы. */
    public record TeamNamesView(boolean canEdit, boolean useRussianNames, int translated,
                                int missing, List<TeamNameItem> teams, String note) {
    }

    public record SaveResult(String original, String russian, String message) {
    }

    private final GroupAccess access;
    private final TeamNameRepository names;
    private final MatchRepository matches;

    public TeamNamesService(GroupAccess access, TeamNameRepository names, MatchRepository matches) {
        this.access = access;
        this.names = names;
        this.matches = matches;
    }

    // ------------------------------------------------------------------ для остальных сервисов

    /** Словарь для показа названий в группе (с учётом её настройки). */
    @Transactional(readOnly = true)
    public TeamNameMap mapFor(FriendGroup group) {
        return map(group.isUseRussianNames());
    }

    /** Словарь без учёта настройки группы: русские названия, если они есть. */
    @Transactional(readOnly = true)
    public TeamNameMap map(boolean useRussian) {
        Map<String, String> pairs = new HashMap<>();
        for (TeamName t : names.findAll()) {
            pairs.put(t.getOriginal(), t.getRussian());
        }
        return TeamNameMap.of(pairs, useRussian);
    }

    // ------------------------------------------------------------------ экран словаря

    @Transactional(readOnly = true)
    public TeamNamesView view(AppUser user, Long groupId) {
        Membership ms = access.require(user, groupId);
        TeamNameMap dictionary = map(true);

        // список оригинальных названий берём из матчей: именно их и нужно переводить
        TreeSet<String> inMatches = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        inMatches.addAll(matches.findHomeTeamNames(groupId));
        inMatches.addAll(matches.findAwayTeamNames(groupId));
        inMatches.remove(null);

        List<TeamNameItem> teams = new ArrayList<>();
        int translated = 0;
        int missing = 0;
        for (String original : inMatches) {
            String russian = dictionary.russianOf(original);
            if (russian == null) {
                missing++;
            } else {
                translated++;
            }
            teams.add(new TeamNameItem(original, russian, true));
        }
        // сначала те, у кого перевода нет: именно их и нужно заполнить
        teams.sort(Comparator.comparing((TeamNameItem t) -> t.russian() != null)
                .thenComparing(TeamNameItem::original, String.CASE_INSENSITIVE_ORDER));

        String note = missing == 0
                ? "Все команды из матчей переведены. Названия можно поправить в любой момент."
                : "Выберите команду без перевода и впишите русское название. "
                  + "Перевод увидят все группы.";
        return new TeamNamesView(ms.isOwner(), ms.group().isUseRussianNames(), translated, missing,
                teams, note);
    }

    /** Добавляет или меняет русское название. Оригинал — как он приходит от источника. */
    @Transactional
    public SaveResult save(AppUser user, Long groupId, String original, String russian) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        String from = original == null ? "" : original.trim();
        String to = russian == null ? "" : russian.trim();
        if (from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите, какое название переводим");
        }
        if (to.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите русское название");
        }
        if (from.length() > 100 || to.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Название длиннее 100 символов");
        }
        Instant now = Instant.now();
        TeamName entry = names.findByOriginal(from).orElse(null);
        if (entry == null) {
            names.save(new TeamName(from, to, user.getId(), now));
            return new SaveResult(from, to, "Название добавлено в словарь: " + from + " → " + to);
        }
        entry.rename(to, user.getId(), now);
        names.save(entry);
        return new SaveResult(entry.getOriginal(), to, "Название изменено: " + from + " → " + to);
    }

    /** Убирает перевод: команда снова показывается так, как её присылает источник. */
    @Transactional
    public SaveResult remove(AppUser user, Long groupId, String original) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        String from = original == null ? "" : original.trim();
        TeamName entry = names.findByOriginal(from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Такого названия в словаре нет"));
        names.delete(entry);
        return new SaveResult(entry.getOriginal(), null,
                "Перевод убран: " + entry.getOriginal());
    }

    private static void requireOwner(Membership ms) {
        if (!ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Словарь названий пополняет владелец группы");
        }
    }
}
