package com.example.predictor.names;

import com.example.predictor.common.AdminAccess;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Team;
import com.example.predictor.domain.TeamChangeRequest;
import com.example.predictor.notify.CatalogAlerts;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.TeamChangeRequestRepository;
import com.example.predictor.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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

    /**
     * crest — готовая картинка для показа (своя или флаг по коду), crestUrl и countryCode —
     * то, что лежит в справочнике: по ним заполняется окно правки.
     */
    public record TeamDto(Long id, String kind, String name, String crest, String crestUrl,
                          String countryCode) {
    }

    /**
     * amAdmin — смотрит владелец приложения: только он разбирает заявки.
     * canEdit — может ли смотрящий вообще менять справочник.
     * pending — сколько заявок ждёт разбора.
     */
    public record CatalogView(List<TeamDto> teams, int clubs, int nationals, String note,
                              boolean amAdmin, boolean canEdit, long pending) {
    }

    private final TeamRepository teams;
    private final TeamChangeRequestRepository requests;
    private final GroupMemberRepository members;
    private final AppUserRepository users;
    private final AdminAccess admins;
    private final CatalogAlerts alerts;
    private final String flagTemplate;

    public TeamCatalogService(TeamRepository teams, TeamChangeRequestRepository requests,
                              GroupMemberRepository members, AppUserRepository users,
                              AdminAccess admins, CatalogAlerts alerts,
                              @Value("${app.flags.url-template:https://flagcdn.com/w160/{code}.png}")
                              String flagTemplate) {
        this.teams = teams;
        this.requests = requests;
        this.members = members;
        this.users = users;
        this.admins = admins;
        this.alerts = alerts;
        this.flagTemplate = flagTemplate;
    }

    @Transactional(readOnly = true)
    public CatalogView list(AppUser user, String kindName, String query) {
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
        boolean amAdmin = admins.isAdmin(user);
        boolean canEdit = amAdmin || members.ownsAnyGroup(user.getId());
        if (!canEdit) {
            note = "Справочник общий для всех групп, поэтому меняют его владельцы групп.";
        } else if (!amAdmin) {
            note += " Переименование существующей команды увидят все группы, поэтому оно "
                    + "сначала уходит на одобрение.";
        }
        return new CatalogView(dtos, (int) teams.countByKind(Team.Kind.CLUB),
                (int) teams.countByKind(Team.Kind.NATIONAL), note, amAdmin, canEdit,
                amAdmin ? requests.countByStatus(TeamChangeRequest.Status.PENDING) : 0);
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
        return new TeamDto(team.getId(), team.getKind().name(), team.getName(), crestOf(team),
                team.getCrest(), team.getCountryCode());
    }

    @Transactional
    public TeamDto add(AppUser user, String kindName, String name, String crest, String countryCode) {
        requireEditor(user);
        Team.Kind kind = kind(kindName);
        String clean = clean(name, 100, "Укажите название команды");
        teams.findByKindAndNameIgnoreCase(kind, clean).ifPresent(t -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Такая команда уже есть в справочнике");
        });
        Team team = new Team(kind, clean, checkUrl(crest), checkCode(countryCode), user.getId());
        teams.save(team);
        // новая команда никому не мешает, поэтому появляется сразу — но владелец приложения
        // должен знать, что в общем справочнике прибыло
        if (!admins.isAdmin(user)) {
            alerts.teamAdded(user, clean);
        }
        return toDto(team);
    }

    /**
     * Правка существующей команды. У владельца приложения применяется сразу, у владельца
     * группы становится заявкой: справочник один на все группы, и переименование видно всем.
     */
    @Transactional
    public TeamDto edit(AppUser user, Long id, String name, String crest, String countryCode) {
        requireEditor(user);
        Team team = require(id);
        String clean = clean(name, 100, "Укажите название команды");
        teams.findByKindAndNameIgnoreCase(team.getKind(), clean).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Команда с таким названием уже есть");
            }
        });
        String url = checkUrl(crest);
        String code = checkCode(countryCode);
        if (!admins.isAdmin(user)) {
            requests.save(new TeamChangeRequest(team.getId(), user.getId(),
                    TeamChangeRequest.Kind.EDIT, clean, url, code, Instant.now()));
            alerts.teamChangeAsked(user, team.getName(), clean, false);
            // в справочнике пока всё по-старому: ждём одобрения
            return toDto(team);
        }
        team.change(clean, url, code);
        teams.save(team);
        return toDto(team);
    }

    @Transactional
    public void remove(AppUser user, Long id) {
        requireEditor(user);
        Team team = require(id);
        if (!admins.isAdmin(user)) {
            requests.save(new TeamChangeRequest(team.getId(), user.getId(),
                    TeamChangeRequest.Kind.DELETE, team.getName(), null, null, Instant.now()));
            alerts.teamChangeAsked(user, team.getName(), null, true);
            return;
        }
        teams.delete(team);
    }

    // ------------------------------------------------------------------ заявки

    /** Заявка глазами владельца приложения: что было, что просят и кто просит. */
    public record RequestDto(Long id, Long teamId, String kind, String author, String wasName,
                             String nowName, String crest, String countryCode, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<RequestDto> pending(AppUser user) {
        admins.require(user);
        List<RequestDto> list = new ArrayList<>();
        for (TeamChangeRequest r : requests.findByStatusOrderByIdAsc(TeamChangeRequest.Status.PENDING)) {
            Team team = teams.findById(r.getTeamId()).orElse(null);
            String author = users.findById(r.getAuthorId())
                    .map(AppUser::getUsername).orElse("кто-то");
            list.add(new RequestDto(r.getId(), r.getTeamId(), r.getKind().name(), author,
                    team == null ? "команда удалена" : team.getName(), r.getName(), r.getCrest(),
                    r.getCountryCode(), r.getCreatedAt()));
        }
        return list;
    }

    @Transactional
    public String approve(AppUser user, Long requestId) {
        admins.require(user);
        TeamChangeRequest request = pendingRequest(requestId);
        Team team = teams.findById(request.getTeamId()).orElse(null);
        if (team == null) {
            request.decide(TeamChangeRequest.Status.REJECTED, user.getId(), Instant.now());
            return "Команды уже нет в справочнике, заявка закрыта";
        }
        String message;
        if (request.getKind() == TeamChangeRequest.Kind.DELETE) {
            teams.delete(team);
            message = "Команда убрана из справочника";
        } else {
            team.change(request.getName(), request.getCrest(), request.getCountryCode());
            teams.save(team);
            message = "Новое название сохранено";
        }
        request.decide(TeamChangeRequest.Status.APPROVED, user.getId(), Instant.now());
        return message;
    }

    @Transactional
    public String reject(AppUser user, Long requestId) {
        admins.require(user);
        pendingRequest(requestId).decide(TeamChangeRequest.Status.REJECTED, user.getId(),
                Instant.now());
        return "Заявка отклонена, в справочнике всё осталось как было";
    }

    private TeamChangeRequest pendingRequest(Long id) {
        TeamChangeRequest request = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Заявка не найдена"));
        if (request.getStatus() != TeamChangeRequest.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта заявка уже разобрана");
        }
        return request;
    }

    /** Справочник один на все группы, поэтому менять его может только владелец группы. */
    private void requireEditor(AppUser user) {
        if (admins.isAdmin(user)) {
            return;
        }
        if (!members.ownsAnyGroup(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Справочник команд общий для всех групп: менять его может владелец группы");
        }
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
