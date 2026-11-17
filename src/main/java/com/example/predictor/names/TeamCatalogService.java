package com.example.predictor.names;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Team;
import com.example.predictor.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Справочник команд. Общий на всё приложение, как и словарь русских названий: команды одни
 * и те же для всех групп, и заводить их каждому заново смысла нет.
 *
 * <p>Флаги сборных не хранятся картинками: у команды есть код страны, а адрес собирается
 * по шаблону. Так двести флагов занимают двести слов, а не двести файлов, и переезд картинок
 * лечится одной настройкой.
 */
@Service
public class TeamCatalogService {

    /** Сколько команд отдаём за раз: список листается поиском, а не прокруткой. */
    private static final int LIMIT = 60;

    public record TeamDto(Long id, String kind, String name, String crest) {
    }

    public record CatalogView(List<TeamDto> teams, int clubs, int nationals, String note) {
    }

    private final TeamRepository teams;
    private final String flagTemplate;

    public TeamCatalogService(TeamRepository teams,
                              @Value("${app.flags.url-template:https://flagcdn.com/w160/{code}.png}")
                              String flagTemplate) {
        this.teams = teams;
        this.flagTemplate = flagTemplate;
    }

    @Transactional(readOnly = true)
    public CatalogView list(String kindName, String query) {
        Team.Kind kind = kind(kindName);
        List<Team> found = query == null || query.isBlank()
                ? teams.findByKindOrderByNameAsc(kind, PageRequest.of(0, LIMIT))
                : teams.search(kind, query.trim(), PageRequest.of(0, LIMIT));
        List<TeamDto> dtos = new ArrayList<>();
        for (Team team : found) {
            dtos.add(toDto(team));
        }
        String note = kind == Team.Kind.NATIONAL
                ? "Сборные уже заведены со флагами. Можно добавить свою команду или поправить флаг."
                : "Клубы собраны из загруженных матчей. Если клуба нет, добавьте его — эмблему "
                        + "можно указать ссылкой на картинку.";
        return new CatalogView(dtos, (int) teams.countByKind(Team.Kind.CLUB),
                (int) teams.countByKind(Team.Kind.NATIONAL), note);
    }

    @Transactional(readOnly = true)
    public Team require(Long id) {
        return teams.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Команды нет в справочнике"));
    }

    /** Эмблема команды: своя ссылка или флаг по коду страны. */
    public String crestOf(Team team) {
        if (team.getCrest() != null && !team.getCrest().isBlank()) {
            return team.getCrest();
        }
        if (team.getCountryCode() == null || team.getCountryCode().isBlank()) {
            return null;
        }
        return flagTemplate.replace("{code}", team.getCountryCode().toLowerCase());
    }

    public TeamDto toDto(Team team) {
        return new TeamDto(team.getId(), team.getKind().name(), team.getName(), crestOf(team));
    }

    @Transactional
    public TeamDto add(AppUser user, String kindName, String name, String crest, String countryCode) {
        Team.Kind kind = kind(kindName);
        String clean = clean(name, 100, "Укажите название команды");
        teams.findByKindAndNameIgnoreCase(kind, clean).ifPresent(t -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Такая команда уже есть в справочнике");
        });
        Team team = new Team(kind, clean, checkUrl(crest), checkCode(countryCode), user.getId());
        teams.save(team);
        return toDto(team);
    }

    @Transactional
    public TeamDto edit(AppUser user, Long id, String name, String crest, String countryCode) {
        Team team = require(id);
        String clean = clean(name, 100, "Укажите название команды");
        teams.findByKindAndNameIgnoreCase(team.getKind(), clean).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Команда с таким названием уже есть");
            }
        });
        team.change(clean, checkUrl(crest), checkCode(countryCode));
        teams.save(team);
        return toDto(team);
    }

    @Transactional
    public void remove(AppUser user, Long id) {
        teams.delete(require(id));
    }

    private static Team.Kind kind(String name) {
        if (name == null) {
            return Team.Kind.CLUB;
        }
        try {
            return Team.Kind.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Бывают клубы (CLUB) и сборные (NATIONAL)");
        }
    }

    private static String clean(String value, int max, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank() || clean.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return clean;
    }

    /** Картинку берём только по https: по http телефон её не покажет. */
    private static String checkUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String clean = url.trim();
        if (!clean.startsWith("https://") || clean.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылка на картинку должна начинаться с https:// и быть короче 300 знаков");
        }
        return clean;
    }

    private static String checkCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String clean = code.trim().toLowerCase();
        if (!clean.matches("[a-z]{2}(-[a-z]{3})?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Код страны пишется двумя буквами: ru, de, br. У Англии и Шотландии это gb-eng и gb-sct");
        }
        return clean;
    }
}
