package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Competition;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.RoundMatch;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.group.RulesService;
import com.example.predictor.group.RulesService.Rules;
import com.example.predictor.names.TeamNameMap;
import com.example.predictor.names.TeamNamesService;
import com.example.predictor.repository.CompetitionRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundMatchRepository;
import com.example.predictor.round.SelectionDtos.*;
import com.example.predictor.standings.RoundWeeks;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Отбор матчей в тур. Владелец группы заходит в турнир, отмечает интересные матчи — и только они
 * попадают в тур. Матчи турниров, которых нет в бесплатном тарифе football-data.org (Лига Европы,
 * чемпионат России, сборные), владелец добавляет руками и сам вводит их счёт.
 */
@Service
public class MatchSelectionService {

    private final GroupAccess access;
    private final RulesService rulesService;
    private final CompetitionRepository competitions;
    private final MatchRepository matches;
    private final RoundMatchRepository selection;
    private final PredictionRepository predictions;
    private final AppTime time;
    private final TeamNamesService teamNames;

    public MatchSelectionService(GroupAccess access, RulesService rulesService,
                                 CompetitionRepository competitions, MatchRepository matches,
                                 RoundMatchRepository selection, PredictionRepository predictions,
                                 AppTime time, TeamNamesService teamNames) {
        this.teamNames = teamNames;
        this.access = access;
        this.rulesService = rulesService;
        this.competitions = competitions;
        this.matches = matches;
        this.selection = selection;
        this.predictions = predictions;
        this.time = time;
    }

    // ------------------------------------------------------------------ просмотр

    @Transactional(readOnly = true)
    public List<CompetitionDto> competitions(AppUser user, Long groupId) {
        access.require(user, groupId);
        return competitions.findByEnabledTrueOrderBySortOrderAscNameAsc().stream()
                .map(MatchSelectionService::toDto)
                .toList();
    }

    /**
     * Матчи турнира, которые можно отобрать в тур. Если турнир не указан, берётся первый в списке.
     * Смотреть может любой участник, менять состав тура — только владелец.
     */
    @Transactional(readOnly = true)
    public SelectionView selectable(AppUser user, Long groupId, LocalDate week, Long competitionId) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        requireMonday(week);

        List<Competition> all = competitions.findByEnabledTrueOrderBySortOrderAscNameAsc();
        if (all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Список турниров пуст");
        }
        Competition chosen = null;
        for (Competition c : all) {
            if (competitionId == null || c.getId().equals(competitionId)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Турнир не найден");
        }

        List<RoundMatch> selected = selection.findByGroupIdAndRoundWeek(groupId, week);
        Set<Long> selectedIds = new HashSet<>();
        for (RoundMatch rm : selected) {
            selectedIds.add(rm.getMatchId());
        }

        List<FootballMatch> list = matches.findForSelection(chosen.getId(), groupId, week, week);
        Set<Long> predicted = list.isEmpty() ? Set.of()
                : new HashSet<>(predictions.findPredictedMatchIds(groupId, ids(list)));

        Rules rules = rulesService.rulesFor(group, week);
        TeamNameMap names = teamNames.mapFor(group);
        Instant now = Instant.now();
        List<SelectableMatch> views = new ArrayList<>();
        for (FootballMatch m : list) {
            Instant lockAt = rules.lockAt(m.getKickoffAt());
            views.add(new SelectableMatch(m.getId(), names.display(m.getHomeTeam()),
                    names.display(m.getAwayTeam()), m.getKickoffAt(),
                    lockAt, m.getStage(), m.getMatchday(), m.getStatus(), m.isManual(),
                    selectedIds.contains(m.getId()),
                    RevealPolicy.locked(now, lockAt, m.getStatus()),
                    predicted.contains(m.getId()), m.getHomeScore(), m.getAwayScore()));
        }

        String note = chosen.isSynced()
                ? "Отметьте матчи, на которые делаем прогнозы. Матч, на который уже есть прогнозы, "
                + "убрать из тура нельзя."
                : "Матчи этого турнира добавляются вручную: укажите команды и время начала, "
                + "а после игры введите счёт.";
        return new SelectionView(week, chosen.getId(), ms.isOwner(), !chosen.isSynced(),
                selected.size(), all.stream().map(MatchSelectionService::toDto).toList(), views, note);
    }

    // ------------------------------------------------------------------ состав тура

    /** Добавляет матчи в тур группы. Неделя тура берётся у самого матча. */
    @Transactional
    public SelectionResult add(AppUser user, Long groupId, List<Long> matchIds) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        LocalDate last = time.lastPredictableWeek();
        LocalDate first = time.currentWeek();
        TeamNameMap teamLabels = teamNames.mapFor(ms.group());
        Instant now = Instant.now();

        int added = 0;
        int lockedSkipped = 0;
        LocalDate week = null;
        for (Long matchId : new ArrayList<>(new java.util.LinkedHashSet<>(matchIds))) {
            FootballMatch m = matches.findById(matchId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Матч не найден"));
            if (m.getOwnerGroupId() != null && !m.getOwnerGroupId().equals(groupId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Матч не найден");
            }
            week = m.getRoundWeek();
            String title = teamLabels.display(m.getHomeTeam()) + " — "
                    + teamLabels.display(m.getAwayTeam());
            fail(SelectionRules.checkWeek(title, week, first, last));
            Rules rules = rulesService.rulesFor(ms.group(), week);
            if (RevealPolicy.locked(now, rules.lockAt(m.getKickoffAt()), m.getStatus())) {
                // «Выбрать все» может задеть матч, приём на который только что закрылся:
                // пропускаем его, а остальные добавляем
                lockedSkipped++;
                continue;
            }
            if (selection.findByGroupIdAndMatchId(groupId, matchId).isPresent()) {
                continue; // уже в туре
            }
            selection.save(new RoundMatch(groupId, week, matchId, user.getId(), now));
            added++;
        }
        if (added == 0 && lockedSkipped > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Приём прогнозов на эти матчи уже закрыт, добавить их в тур нельзя");
        }
        int total = week == null ? 0 : selection.findByGroupIdAndRoundWeek(groupId, week).size();
        String message = added == 0
                ? "Эти матчи уже в туре"
                : "Матчей добавлено в тур: " + added
                  + (lockedSkipped > 0 ? ". Пропущено, приём уже закрыт: " + lockedSkipped : "");
        return new SelectionResult(total, added, message);
    }

    /** Убирает матч из тура. Если на него уже есть прогнозы, убрать нельзя. */
    @Transactional
    public SelectionResult remove(AppUser user, Long groupId, Long matchId) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        RoundMatch rm = selection.findByGroupIdAndMatchId(groupId, matchId).orElse(null);
        fail(SelectionRules.canRemove(rm != null,
                rm != null && predictions.existsForMatch(groupId, matchId)));
        LocalDate week = rm.getRoundWeek();
        selection.delete(rm);
        return new SelectionResult(selection.findByGroupIdAndRoundWeek(groupId, week).size(), 1,
                "Матч убран из тура");
    }

    // ------------------------------------------------------------------ ручные матчи

    /** Владелец добавляет матч сам: турнира нет в источнике или матч там не нашёлся. */
    @Transactional
    public ManualMatchResult createManual(AppUser user, Long groupId, ManualMatchRequest request) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        Competition competition = competitions.findById(request.competitionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Турнир не найден"));

        LocalDate week = RoundWeeks.mondayOf(request.kickoffAt(), time.zone());
        fail(SelectionRules.canPlaceManual(week, time.currentWeek(), time.lastPredictableWeek()));
        fail(SelectionRules.checkTeams(request.homeTeam(), request.awayTeam()));
        String home = request.homeTeam().trim();
        String away = request.awayTeam().trim();
        String stage = request.stage() == null || request.stage().isBlank()
                ? null : request.stage().trim();

        FootballMatch match = new FootballMatch(competition, groupId, user.getId(), home, away,
                request.kickoffAt(), week, stage);
        matches.save(match);
        matches.flush();
        selection.save(new RoundMatch(groupId, week, match.getId(), user.getId(), Instant.now()));
        return new ManualMatchResult(match.getId(), week, "Матч добавлен в тур");
    }

    /** Правит ручной матч, пока на него нет прогнозов. */
    @Transactional
    public ManualMatchResult editManual(AppUser user, Long groupId, Long matchId,
                                        ManualMatchRequest request) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        FootballMatch match = requireManual(groupId, matchId);
        if (predictions.existsForMatch(groupId, matchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "На этот матч уже есть прогнозы, менять его нельзя");
        }
        LocalDate week = RoundWeeks.mondayOf(request.kickoffAt(), time.zone());
        fail(SelectionRules.canPlaceManual(week, time.currentWeek(), time.lastPredictableWeek()));
        fail(SelectionRules.checkTeams(request.homeTeam(), request.awayTeam()));
        Competition competition = competitions.findById(request.competitionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Турнир не найден"));
        match.setCompetition(competition);
        match.editManual(request.homeTeam().trim(), request.awayTeam().trim(), request.kickoffAt(),
                week, request.stage() == null || request.stage().isBlank() ? null : request.stage().trim());
        matches.save(match);

        // неделя матча могла измениться: переносим его в нужный тур
        selection.findByGroupIdAndMatchId(groupId, matchId).ifPresent(rm -> {
            if (!rm.getRoundWeek().equals(week)) {
                selection.delete(rm);
                selection.save(new RoundMatch(groupId, week, matchId, user.getId(), Instant.now()));
            }
        });
        return new ManualMatchResult(matchId, week, "Матч изменён");
    }

    /** Владелец вводит счёт ручного матча: из источника он не придёт. */
    @Transactional
    public ManualMatchResult setScore(AppUser user, Long groupId, Long matchId,
                                      ManualScoreRequest request) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        FootballMatch match = requireManual(groupId, matchId);
        Rules rules = rulesService.rulesFor(ms.group(), match.getRoundWeek());
        Instant now = Instant.now();
        boolean locked = RevealPolicy.locked(now, rules.lockAt(match.getKickoffAt()), match.getStatus());
        fail(SelectionRules.checkManualScore(locked, request.extraHome(), request.extraAway(),
                request.penHome(), request.penAway()));
        match.setManualScore(request.homeScore(), request.awayScore(), request.extraHome(),
                request.extraAway(), request.penHome(), request.penAway());
        matches.save(match);
        return new ManualMatchResult(matchId, match.getRoundWeek(),
                "Счёт сохранён: " + match.getHomeScore() + ":" + match.getAwayScore());
    }

    /** Матч не состоялся: убираем его из подсчёта, не удаляя прогнозы. */
    @Transactional
    public ManualMatchResult cancelManual(AppUser user, Long groupId, Long matchId) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        FootballMatch match = requireManual(groupId, matchId);
        match.cancelManual();
        matches.save(match);
        return new ManualMatchResult(matchId, match.getRoundWeek(),
                "Матч отменён и в итогах тура не учитывается");
    }

    // ------------------------------------------------------------------ вспомогательное

    private FootballMatch requireManual(Long groupId, Long matchId) {
        FootballMatch match = matches.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Матч не найден"));
        if (!match.isManual() || !groupId.equals(match.getOwnerGroupId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Так можно менять только матчи, добавленные в этой группе вручную");
        }
        return match;
    }

    /** Превращает причину отказа в ответ API. null значит «всё в порядке». */
    private static void fail(SelectionRules.Problem problem) {
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.valueOf(problem.status()), problem.message());
        }
    }

    private static void requireOwner(Membership ms) {
        if (!ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Состав тура определяет владелец группы");
        }
    }

    private static List<Long> ids(List<FootballMatch> list) {
        List<Long> ids = new ArrayList<>(list.size());
        for (FootballMatch m : list) {
            ids.add(m.getId());
        }
        return ids;
    }

    private static CompetitionDto toDto(Competition c) {
        return new CompetitionDto(c.getId(), c.getCode(), c.getName(), c.getShortName(), c.isSynced());
    }

    private static void requireMonday(LocalDate week) {
        if (!RoundWeeks.mondayOf(week).equals(week)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Тур обозначается датой понедельника, например 2026-11-09");
        }
    }
}
