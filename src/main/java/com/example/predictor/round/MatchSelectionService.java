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
import java.util.Map;
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
    private final com.example.predictor.repository.GroupRoundRepository rounds;
    private final com.example.predictor.repository.RoundResultRepository finishedRounds;
    private final PredictionRepository predictions;
    private final AppTime time;
    private final TeamNamesService teamNames;
    private final com.example.predictor.repository.GroupAutoCompetitionRepository autos;
    private final AutoSelectionService autoSelection;
    private final com.example.predictor.names.TeamCatalogService catalog;

    public MatchSelectionService(GroupAccess access, RulesService rulesService,
                                 CompetitionRepository competitions, MatchRepository matches,
                                 RoundMatchRepository selection, PredictionRepository predictions,
                                 AppTime time, TeamNamesService teamNames,
                                 com.example.predictor.repository.GroupAutoCompetitionRepository autos,
                                 AutoSelectionService autoSelection,
                                 com.example.predictor.names.TeamCatalogService catalog,
                                com.example.predictor.repository.GroupRoundRepository rounds,
                                com.example.predictor.repository.RoundResultRepository finishedRounds) {
        this.catalog = catalog;
        this.autos = autos;
        this.autoSelection = autoSelection;
        this.teamNames = teamNames;
        this.access = access;
        this.rulesService = rulesService;
        this.competitions = competitions;
        this.matches = matches;
        this.selection = selection;
        this.rounds = rounds;
        this.finishedRounds = finishedRounds;
        this.predictions = predictions;
        this.time = time;
    }

    // ------------------------------------------------------------------ просмотр

    @Transactional(readOnly = true)
    public List<CompetitionDto> competitions(AppUser user, Long groupId) {
        access.require(user, groupId);
        Set<Long> auto = autoIds(groupId);
        return competitions.findByEnabledTrueOrderBySortOrderAscNameAsc().stream()
                .map(c -> toDto(c, auto))
                .toList();
    }

    /**
     * Матчи турнира, которые можно отобрать в тур. Если турнир не указан, берётся первый в списке.
     * Смотреть может любой участник, менять состав тура — только владелец.
     */
    @Transactional(readOnly = true)
    public SelectionView selectable(AppUser user, Long groupId, LocalDate week, Long competitionId) {
        return selectable(user, groupId, week, null, competitionId);
    }

    /**
     * То же, но для конкретного тура недели: в неделе их может быть несколько, и отметки
     * относятся к выбранному. Матч, занятый соседним туром, виден, но взять его нельзя —
     * он живёт ровно в одном туре.
     */
    @Transactional(readOnly = true)
    public SelectionView selectable(AppUser user, Long groupId, LocalDate week, LocalDate roundKey,
                                    Long competitionId) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        requireMonday(week);
        LocalDate key = roundKey == null ? week : roundKey;
        if (!RoundKeys.weekOf(key).equals(week)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тур не из этой недели");
        }

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

        List<RoundMatch> selected = selection.findByGroupIdAndRoundWeek(groupId, key);
        Set<Long> selectedIds = new HashSet<>();
        for (RoundMatch rm : selected) {
            selectedIds.add(rm.getMatchId());
        }
        // матчи, занятые другими турами той же недели
        Set<Long> takenElsewhere = new HashSet<>();
        for (RoundMatch rm : selection.findByGroupIdAndRoundWeekBetween(groupId, week,
                week.plusDays(RoundKeys.MAX_PER_WEEK - 1L))) {
            if (!rm.getRoundWeek().equals(key)) {
                takenElsewhere.add(rm.getMatchId());
            }
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
                    names.display(m.getAwayTeam()), m.getHomeCrest(), m.getAwayCrest(),
                    m.getKickoffAt(),
                    lockAt, StageLabels.of(m.getStage()), m.getMatchday(), m.getStatus(), m.isManual(),
                    selectedIds.contains(m.getId()),
                    RevealPolicy.locked(now, lockAt, m.getStatus()),
                    predicted.contains(m.getId()), m.getHomeScore(), m.getAwayScore(),
                    takenElsewhere.contains(m.getId())));
        }

        String note = chosen.isSynced()
                ? "Отметьте матчи, на которые делаем прогнозы. Матч, на который уже есть прогнозы, "
                + "убрать из тура нельзя."
                : "Матчи этого турнира добавляются вручную: укажите команды и время начала, "
                + "а после игры введите счёт.";
        Set<Long> auto = autoIds(groupId);
        com.example.predictor.domain.GroupRound round =
                rounds.findByGroupIdAndRoundKey(groupId, key).orElse(null);
        return new SelectionView(week, chosen.getId(), GroupAccess.canSelectMatches(ms), !chosen.isSynced(),
                auto.contains(chosen.getId()), selected.size(),
                all.stream().map(c -> toDto(c, auto)).toList(), views, note,
                key, RoundKeys.noOf(key), round == null ? null : round.getTitle(),
                round != null && round.isFriendly(), roundsOfWeek(groupId, week));
    }

    // ------------------------------------------------------------------ состав тура

    /** Добавляет матчи в первый тур их недели. */
    @Transactional
    public SelectionResult add(AppUser user, Long groupId, List<Long> matchIds) {
        return add(user, groupId, matchIds, null);
    }

    /**
     * Добавляет матчи в указанный тур. Тур не указан — первый тур недели самого матча, как
     * было до появления нескольких туров в неделе.
     */
    @Transactional
    public SelectionResult add(AppUser user, Long groupId, List<Long> matchIds, LocalDate roundKey) {
        Membership ms = access.require(user, groupId);
        requireSelector(ms);
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
            String title = teamLabels.display(m.getHomeTeam()) + " — "
                    + teamLabels.display(m.getAwayTeam());
            fail(SelectionRules.checkWeek(title, m.getRoundWeek(), first, last));
            if (roundKey != null && !RoundKeys.weekOf(roundKey).equals(m.getRoundWeek())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Матч " + title + " относится к другой неделе, в этот тур его не положить");
            }
            week = roundKey == null ? m.getRoundWeek() : roundKey;
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
            ensureRound(groupId, week, user.getId(), now);
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
        requireSelector(ms);
        RoundMatch rm = selection.findByGroupIdAndMatchId(groupId, matchId).orElse(null);
        fail(SelectionRules.canRemove(rm != null,
                rm != null && predictions.existsForMatch(groupId, matchId)));
        LocalDate week = rm.getRoundWeek();
        selection.delete(rm);
        return new SelectionResult(selection.findByGroupIdAndRoundWeek(groupId, week).size(), 1,
                "Матч убран из тура");
    }

    // ------------------------------------------------------------------ туры недели

    /**
     * Новый тур в неделе: Лига чемпионов отдельным зачётом, чемпионаты другим. Ключом служит
     * первый свободный день недели, но участник его не видит — тур подписан своими матчами
     * и названием, если его задали.
     */
    @Transactional
    public SelectionDtos.RoundInfo createRound(AppUser user, Long groupId, LocalDate week,
                                               String title, boolean friendly) {
        Membership ms = access.require(user, groupId);
        requireSelector(ms);
        requireMonday(week);
        fail(SelectionRules.checkWeek("новый тур", week, time.currentWeek(),
                time.lastPredictableWeek()));

        LocalDate key = null;
        for (int no = 1; no <= RoundKeys.MAX_PER_WEEK; no++) {
            LocalDate candidate = RoundKeys.keyFor(week, no);
            if (!rounds.existsByGroupIdAndRoundKey(groupId, candidate)) {
                key = candidate;
                break;
            }
        }
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "В одной неделе больше " + RoundKeys.MAX_PER_WEEK + " туров не бывает");
        }
        String clean = clean(title);
        rounds.save(new com.example.predictor.domain.GroupRound(groupId, key, clean, friendly,
                Instant.now(), user.getId()));
        return new SelectionDtos.RoundInfo(key, RoundKeys.noOf(key), clean, friendly,
                friendly
                        ? "Товарищеский тур создан: победитель будет, а шаурмы никто не должен."
                        : "Тур создан. Отметьте матчи, которые в него войдут.");
    }

    /** Переименовать тур: пустое название — снова по датам матчей. */
    @Transactional
    public SelectionDtos.RoundInfo renameRound(AppUser user, Long groupId, LocalDate roundKey,
                                               String title) {
        Membership ms = access.require(user, groupId);
        requireSelector(ms);
        com.example.predictor.domain.GroupRound round = rounds
                .findByGroupIdAndRoundKey(groupId, roundKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Тур не найден"));
        String clean = clean(title);
        round.rename(clean);
        return new SelectionDtos.RoundInfo(roundKey, RoundKeys.noOf(roundKey), clean,
                round.isFriendly(), clean == null ? "Название убрано" : "Название сохранено");
    }

    /**
     * Переключить тур между игрой на шаурму и товарищеским. Пока итоги не подведены:
     * после них долги уже записаны, и менять правила задним числом нечестно.
     */
    @Transactional
    public SelectionDtos.RoundInfo setFriendly(AppUser user, Long groupId, LocalDate roundKey,
                                               boolean friendly) {
        Membership ms = access.require(user, groupId);
        requireSelector(ms);
        com.example.predictor.domain.GroupRound round = rounds
                .findByGroupIdAndRoundKey(groupId, roundKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Тур не найден"));
        if (finishedRounds.existsByGroupIdAndRoundWeek(groupId, roundKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Итоги тура уже подведены, менять правила задним числом поздно");
        }
        round.setFriendly(friendly);
        return new SelectionDtos.RoundInfo(roundKey, RoundKeys.noOf(roundKey), round.getTitle(),
                friendly,
                friendly
                        ? "Товарищеский тур: победителя посчитаем, шаурму — нет"
                        : "Играем на шаурму: проигравший кормит победителя");
    }

    /** Туры недели с их матчами: из чего выбирать, когда кладёшь матчи в тур. */
    private List<SelectionDtos.RoundOption> roundsOfWeek(Long groupId, LocalDate week) {
        LocalDate lastKey = week.plusDays(RoundKeys.MAX_PER_WEEK - 1L);
        Map<LocalDate, List<Long>> matchesOf = new java.util.TreeMap<>();
        for (RoundMatch rm : selection.findByGroupIdAndRoundWeekBetween(groupId, week, lastKey)) {
            matchesOf.computeIfAbsent(rm.getRoundWeek(), k -> new ArrayList<>()).add(rm.getMatchId());
        }
        java.util.TreeSet<LocalDate> keys = new java.util.TreeSet<>(matchesOf.keySet());
        for (com.example.predictor.domain.GroupRound round
                : rounds.findByGroupIdAndRoundKeyBetweenOrderByRoundKeyAsc(groupId, week, lastKey)) {
            keys.add(round.getRoundKey());
        }
        keys.add(week); // первый тур недели есть всегда, даже пока он пуст

        List<SelectionDtos.RoundOption> options = new ArrayList<>();
        for (LocalDate key : keys) {
            List<Long> ids = matchesOf.getOrDefault(key, List.of());
            Instant firstKickoff = null;
            Instant lastKickoff = null;
            for (FootballMatch m : matches.findAllById(ids)) {
                if (firstKickoff == null || m.getKickoffAt().isBefore(firstKickoff)) {
                    firstKickoff = m.getKickoffAt();
                }
                if (lastKickoff == null || m.getKickoffAt().isAfter(lastKickoff)) {
                    lastKickoff = m.getKickoffAt();
                }
            }
            String title = rounds.findByGroupIdAndRoundKey(groupId, key)
                    .map(com.example.predictor.domain.GroupRound::getTitle)
                    .orElse(null);
            boolean friendly = rounds.findByGroupIdAndRoundKey(groupId, key)
                    .map(com.example.predictor.domain.GroupRound::isFriendly).orElse(false);
            options.add(new SelectionDtos.RoundOption(key, RoundKeys.noOf(key), title, ids.size(),
                    firstKickoff, lastKickoff, friendly));
        }
        return options;
    }

    /** Тур мог появиться раньше этой таблицы: тогда заводим запись о нём сейчас. */
    private void ensureRound(Long groupId, LocalDate key, Long userId, Instant now) {
        if (!rounds.existsByGroupIdAndRoundKey(groupId, key)) {
            rounds.save(new com.example.predictor.domain.GroupRound(groupId, key, null, false, now,
                    userId));
        }
    }

    private static String clean(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(60, trimmed.length()));
    }

    // ------------------------------------------------------------------ ручные матчи

    /** Владелец добавляет матч сам: турнира нет в источнике или матч там не нашёлся. */
    @Transactional
    public ManualMatchResult createManual(AppUser user, Long groupId, ManualMatchRequest request) {
        Membership ms = access.require(user, groupId);
        requireSelector(ms);
        Competition competition = competitions.findById(request.competitionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Турнир не найден"));

        LocalDate week = RoundWeeks.mondayOf(request.kickoffAt(), time.zone());
        fail(SelectionRules.canPlaceManual(week, time.currentWeek(), time.lastPredictableWeek()));

        // команды либо выбраны из справочника, либо набраны руками (старые версии приложения)
        Picked picked = pick(request);
        fail(SelectionRules.checkTeams(picked.home(), picked.away()));
        String stage = request.stage() == null || request.stage().isBlank()
                ? null : request.stage().trim();

        FootballMatch match = new FootballMatch(competition, groupId, user.getId(),
                picked.home(), picked.away(), request.kickoffAt(), week, stage);
        match.setCrests(picked.homeCrest(), picked.awayCrest());
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
        requireSelector(ms);
        FootballMatch match = requireManual(groupId, matchId);
        if (predictions.existsForMatch(groupId, matchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "На этот матч уже есть прогнозы, менять его нельзя");
        }
        LocalDate week = RoundWeeks.mondayOf(request.kickoffAt(), time.zone());
        fail(SelectionRules.canPlaceManual(week, time.currentWeek(), time.lastPredictableWeek()));
        Picked picked = pick(request);
        fail(SelectionRules.checkTeams(picked.home(), picked.away()));
        Competition competition = competitions.findById(request.competitionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Турнир не найден"));
        match.setCompetition(competition);
        match.editManual(picked.home(), picked.away(), request.kickoffAt(),
                week, request.stage() == null || request.stage().isBlank() ? null : request.stage().trim());
        match.setCrests(picked.homeCrest(), picked.awayCrest());
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
        requireSelector(ms);
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
        requireSelector(ms);
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

    /**
     * Право собирать тур: у владельца всегда, у остальных — как решила группа в настройках.
     * Автосостав турнира остаётся за владельцем: это правило не на один тур, а на все вперёд.
     */
    private static void requireSelector(Membership ms) {
        if (!GroupAccess.canSelectMatches(ms)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, switch (ms.group().getMatchSelection()) {
                case OWNER -> "Состав тура собирает владелец группы";
                case HELPERS -> "Состав тура собирают владелец группы и его помощники";
                case ANYONE -> "Состав тура сейчас менять нельзя";
            });
        }
    }

    private static void requireOwner(Membership ms) {
        if (!ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Это правило устанавливает владелец группы");
        }
    }

    private static List<Long> ids(List<FootballMatch> list) {
        List<Long> ids = new ArrayList<>(list.size());
        for (FootballMatch m : list) {
            ids.add(m.getId());
        }
        return ids;
    }

    /** Команды матча: названия и эмблемы, откуда бы они ни пришли. */
    private record Picked(String home, String away, String homeCrest, String awayCrest) {
    }

    /**
     * Команда из справочника берётся вместе с эмблемой — набирать название и искать картинку
     * каждый раз не нужно. Если справочник не использовали, остаётся ручной ввод.
     */
    private Picked pick(ManualMatchRequest request) {
        String home = request.homeTeam() == null ? null : request.homeTeam().trim();
        String away = request.awayTeam() == null ? null : request.awayTeam().trim();
        String homeCrest = null;
        String awayCrest = null;
        if (request.homeTeamId() != null) {
            var team = catalog.require(request.homeTeamId());
            home = team.getName();
            homeCrest = catalog.crestOf(team);
        }
        if (request.awayTeamId() != null) {
            var team = catalog.require(request.awayTeamId());
            away = team.getName();
            awayCrest = catalog.crestOf(team);
        }
        if (home == null || home.isBlank() || away == null || away.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Выберите обе команды");
        }
        return new Picked(home, away, homeCrest, awayCrest);
    }

    private static CompetitionDto toDto(Competition c, Set<Long> auto) {
        return new CompetitionDto(c.getId(), c.getCode(), c.getName(), c.getShortName(), c.isSynced(),
                auto.contains(c.getId()));
    }

    private Set<Long> autoIds(Long groupId) {
        Set<Long> ids = new HashSet<>();
        for (var a : autos.findByGroupId(groupId)) {
            ids.add(a.getCompetitionId());
        }
        return ids;
    }

    /**
     * Включает или выключает автосостав турнира: все его матчи попадают в тур сами.
     * При включении матчи ближайших недель добавляются сразу.
     */
    @Transactional
    public SelectionResult setAuto(AppUser user, Long groupId, Long competitionId, boolean enabled) {
        Membership ms = access.require(user, groupId);
        requireOwner(ms);
        Competition competition = competitions.findById(competitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Турнир не найден"));
        if (!competition.isSynced()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Матчи этого турнира добавляются вручную, автосоставу брать их неоткуда");
        }
        var existing = autos.findByGroupIdAndCompetitionId(groupId, competitionId);
        if (!enabled) {
            existing.ifPresent(autos::delete);
            return new SelectionResult(0, 0, "Автосостав выключен: " + competition.getShortName());
        }
        if (existing.isEmpty()) {
            autos.save(new com.example.predictor.domain.GroupAutoCompetition(groupId, competitionId));
        }
        int added = autoSelection.apply(groupId, competitionId);
        return new SelectionResult(0, added, "Автосостав включён: " + competition.getShortName()
                + ". Матчей добавлено в туры: " + added);
    }

    private static void requireMonday(LocalDate week) {
        if (!RoundWeeks.mondayOf(week).equals(week)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Тур обозначается датой понедельника, например 2026-11-09");
        }
    }
}
