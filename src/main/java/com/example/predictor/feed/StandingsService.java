package com.example.predictor.feed;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Competition;
import com.example.predictor.domain.StandingRowEntity;
import com.example.predictor.repository.CompetitionRepository;
import com.example.predictor.repository.StandingRowRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Турнирная таблица самого турнира — не наша, а настоящая.
 *
 * Рядом с прогнозами это полезно: видно, кто в каком положении идёт, и ставить становится
 * интереснее. Берётся у источника раз в час, здесь только показывается.
 */
@Service
public class StandingsService {

    /** Форма: последние матчи буквами W (выигрыш), D (ничья), L (поражение) — «WWDLW». */
    public record RowDto(int position, String team, String crest, int played, int won, int draw,
                         int lost, int goalsFor, int goalsAgainst, int points, String form) {
    }

    public record GroupDto(String title, List<RowDto> rows) {
    }

    public record TableDto(Long competitionId, String competition, List<GroupDto> groups,
                           Instant updatedAt, String note) {
    }

    public record CompetitionDto(Long id, String name, String shortName, boolean hasTable) {
    }

    public record TablesView(List<CompetitionDto> competitions, TableDto table, String note,
                             boolean amAdmin) {
    }

    private final CompetitionRepository competitions;
    private final StandingRowRepository standings;
    private final TeamFormService teamForm;
    private final com.example.predictor.names.TeamNamesService teamNames;
    private final ExtraDataSyncService extra;
    private final com.example.predictor.common.AdminAccess admins;

    public StandingsService(CompetitionRepository competitions, StandingRowRepository standings,
                            TeamFormService teamForm, ExtraDataSyncService extra,
                            com.example.predictor.common.AdminAccess admins,
                            com.example.predictor.names.TeamNamesService teamNames) {
        this.teamNames = teamNames;
        this.competitions = competitions;
        this.standings = standings;
        this.teamForm = teamForm;
        this.extra = extra;
        this.admins = admins;
    }

    /**
     * Обновить таблицы сейчас, не дожидаясь часа. Только владельцу приложения: это запросы
     * к источнику, а их на бесплатном ключе десять в минуту.
     */
    public String refresh(AppUser user) {
        admins.require(user);
        int tables = extra.syncStandings();
        int pairs = extra.syncHeadToHead();
        if (tables == 0 && pairs == 0) {
            return "Источник ничего не отдал: похоже, таблицы на этом ключе недоступны или "
                    + "исчерпан лимит запросов. Загляните в журнал сервера.";
        }
        return "Обновлено таблиц: " + tables + ", личных встреч: " + pairs;
    }

    @Transactional(readOnly = true)
    public TablesView view(AppUser user, Long competitionId) {
        List<Competition> all = competitions.findByEnabledTrueOrderBySortOrderAscNameAsc();
        List<Long> ids = all.stream().map(Competition::getId).toList();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (StandingRowEntity row : standings.findByCompetitionIdIn(ids)) {
            counts.merge(row.getCompetitionId(), 1, Integer::sum);
        }

        // показываем все турниры, которые загружаются автоматически: если таблицы ещё нет,
        // честнее сказать об этом, чем молча спрятать турнир
        List<CompetitionDto> dtos = new ArrayList<>();
        Competition chosen = null;
        Competition firstWithTable = null;
        for (Competition c : all) {
            if (!c.isSynced()) {
                continue;
            }
            boolean has = counts.getOrDefault(c.getId(), 0) > 0;
            dtos.add(new CompetitionDto(c.getId(), c.getName(), c.getShortName(), has));
            if (competitionId != null && c.getId().equals(competitionId)) {
                chosen = c;
            }
            if (has && firstWithTable == null) {
                firstWithTable = c;
            }
        }
        if (chosen == null) {
            chosen = firstWithTable;
        }
        if (chosen == null) {
            return new TablesView(dtos, null,
                    "Таблиц пока нет: они приходят от источника раз в час и только для "
                            + "турниров, которые загружаются автоматически.",
                    admins.isAdmin(user));
        }
        TableDto table = table(chosen);
        if (table.groups().isEmpty()) {
            return new TablesView(dtos,
                    new TableDto(chosen.getId(), chosen.getName(), List.of(), null,
                            "Таблица этого турнира ещё не пришла. Она обновляется раз в час; "
                                    + "у кубковых турниров её может не быть вовсе."),
                    null, admins.isAdmin(user));
        }
        return new TablesView(dtos, table, null, admins.isAdmin(user));
    }

    private TableDto table(Competition competition) {
        // словарь русских названий общий на всё приложение: в таблице он уместен так же,
        // как в матчах, иначе рядом с «Арсеналом» стоял бы «Arsenal FC»
        com.example.predictor.names.TeamNameMap labels = teamNames.map(true);
        List<StandingRowEntity> rows =
                standings.findByCompetitionIdOrderByGroupLabelAscPositionAsc(competition.getId());

        // форму источник присылает не всегда: чего нет, считаем по своим матчам
        List<String> withoutForm = new ArrayList<>();
        for (StandingRowEntity row : rows) {
            if (form(row.getForm()) == null) {
                withoutForm.add(row.getTeamName());
            }
        }
        Map<String, String> computed = teamForm.formOf(withoutForm);

        Map<String, List<RowDto>> byGroup = new LinkedHashMap<>();
        Instant updated = null;
        for (StandingRowEntity row : rows) {
            String group = row.getGroupLabel() == null ? "" : row.getGroupLabel();
            String rowForm = form(row.getForm());
            if (rowForm == null) {
                rowForm = computed.get(row.getTeamName());
            }
            byGroup.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new RowDto(row.getPosition(), labels.display(row.getTeamName()),
                            row.getCrest(),
                            row.getPlayed(), row.getWon(), row.getDraw(), row.getLost(),
                            row.getGoalsFor(), row.getGoalsAgainst(), row.getPoints(), rowForm));
            if (updated == null || row.getUpdatedAt().isAfter(updated)) {
                updated = row.getUpdatedAt();
            }
        }
        List<GroupDto> groups = new ArrayList<>();
        for (Map.Entry<String, List<RowDto>> e : byGroup.entrySet()) {
            groups.add(new GroupDto(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()));
        }
        return new TableDto(competition.getId(), competition.getName(), groups, updated,
                "Таблица турнира приходит от источника и обновляется раз в час.");
    }

    /**
     * «W,W,D,L,W» → «WWDLW»: приложение рисует это кружками, зелёным, жёлтым и красным,
     * поэтому буквы нужны ему как есть, без запятых и без перевода.
     */
    public static String form(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(",")) {
            String letter = part.trim().toUpperCase();
            if (letter.equals("W") || letter.equals("D") || letter.equals("L")) {
                out.append(letter);
            }
        }
        return out.length() == 0 ? null : out.toString();
    }
}
