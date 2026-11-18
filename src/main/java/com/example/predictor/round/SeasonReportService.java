package com.example.predictor.round;

import com.example.predictor.common.UserLabel;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.questions.QuestionService;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.RoundResultRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сезон в двух видах: линия формы и итоги.
 *
 * Таблица зачёта отвечает, кто впереди, но не отвечает, как к этому шли. График формы
 * показывает ход сезона тур за туром, а итоги — то, что хочется вспомнить потом: чемпион,
 * лучший тур, самый точный прогноз, самая длинная серия побед.
 *
 * Номинаций про проигравших здесь нет намеренно: долговая книга и так всё помнит, а итоги
 * сезона — не повод тыкать человека носом.
 */
@Service
public class SeasonReportService {

    /**
     * Линия одного участника. В points может стоять null — значит, в том туре он не играл;
     * totals идёт нарастающим итогом и в таких турах просто не меняется.
     */
    public record FormLine(Long userId, String username, String badge, int place, int total,
                           List<Integer> points, List<Integer> totals) {
    }

    public record FormView(String season, List<LocalDate> weeks, List<FormLine> lines, String note) {
    }

    /** Одна строка итогов: «Чемпион сезона» — «Антон» — «64 балла за 11 туров». */
    public record Nomination(String title, String who, String detail) {
    }

    public record FinaleView(String season, boolean current, int rounds, int players,
                             List<Nomination> nominations, FormView form, String note) {
    }

    /** «9 ноября»: дата тура в итогах читается словами, а не ISO-строкой. */
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.forLanguageTag("ru"));

    private final GroupAccess access;
    private final RoundResultRepository results;
    private final GroupMemberRepository members;
    private final SeasonService seasons;
    private final QuestionService questions;

    public SeasonReportService(GroupAccess access, RoundResultRepository results,
                               GroupMemberRepository members, SeasonService seasons,
                               QuestionService questions) {
        this.access = access;
        this.results = results;
        this.members = members;
        this.seasons = seasons;
        this.questions = questions;
    }

    // ------------------------------------------------------------------ график формы

    @Transactional(readOnly = true)
    public FormView form(AppUser user, Long groupId, Long seasonId) {
        access.require(user, groupId);
        SeasonService.Range range = seasons.rangeOf(groupId, seasonId);
        return build(groupId, range);
    }

    private FormView build(Long groupId, SeasonService.Range range) {
        Map<Long, UserLabel> names = names(groupId);
        List<RoundResult> rows = inRange(groupId, range);
        Map<LocalDate, Map<Long, Integer>> bonus =
                questions.bonusByWeek(groupId, range.from(), range.to());

        // недели по возрастанию: график читается слева направо, как и сезон
        TreeSet<LocalDate> weekSet = new TreeSet<>();
        for (RoundResult r : rows) {
            weekSet.add(r.getRoundWeek());
        }
        weekSet.addAll(bonus.keySet());
        List<LocalDate> weeks = new ArrayList<>(weekSet);

        Map<Long, Map<LocalDate, Integer>> byUser = new LinkedHashMap<>();
        for (RoundResult r : rows) {
            byUser.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                    .merge(r.getRoundWeek(), r.getPoints(), Integer::sum);
        }
        for (Map.Entry<LocalDate, Map<Long, Integer>> e : bonus.entrySet()) {
            for (Map.Entry<Long, Integer> person : e.getValue().entrySet()) {
                byUser.computeIfAbsent(person.getKey(), k -> new HashMap<>())
                        .merge(e.getKey(), person.getValue(), Integer::sum);
            }
        }

        List<FormLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Map<LocalDate, Integer>> e : byUser.entrySet()) {
            List<Integer> points = new ArrayList<>(weeks.size());
            List<Integer> totals = new ArrayList<>(weeks.size());
            int running = 0;
            for (LocalDate week : weeks) {
                Integer scored = e.getValue().get(week);
                if (scored != null) {
                    running += scored;
                }
                points.add(scored);
                totals.add(running);
            }
            UserLabel who = names.getOrDefault(e.getKey(), UserLabel.UNKNOWN);
            lines.add(new FormLine(e.getKey(), who.username(), who.badge(), 0, running,
                    points, totals));
        }
        // впереди тот, у кого больше очков: так подписи к линиям идут в порядке зачёта
        lines.sort(Comparator.comparingInt(FormLine::total).reversed()
                .thenComparing(FormLine::username));
        List<FormLine> placed = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            FormLine line = lines.get(i);
            placed.add(new FormLine(line.userId(), line.username(), line.badge(), i + 1,
                    line.total(), line.points(), line.totals()));
        }

        String note = weeks.isEmpty()
                ? "Сыгранных туров в этом сезоне пока нет: график появится после первых итогов."
                : "Линия — сумма баллов нарастающим итогом. Пропуск тура линию не опускает.";
        return new FormView(range.name(), weeks, placed, note);
    }

    // ------------------------------------------------------------------ итоги сезона

    @Transactional(readOnly = true)
    public FinaleView finale(AppUser user, Long groupId, Long seasonId) {
        access.require(user, groupId);
        SeasonService.Range range = seasons.rangeOf(groupId, seasonId);
        Map<Long, UserLabel> names = names(groupId);
        List<RoundResult> rows = inRange(groupId, range);
        FormView form = build(groupId, range);

        List<Nomination> nominations = new ArrayList<>();
        if (!form.lines().isEmpty()) {
            champion(form, nominations);
            bestRound(rows, names, nominations);
            sharpestEye(rows, names, nominations);
            longestStreak(rows, names, nominations);
        }

        boolean current = range.to() == null;
        String note = rows.isEmpty()
                ? "Итоги появятся, когда в сезоне подведут первый тур."
                : current
                        ? "Сезон ещё идёт — всё это может перемениться."
                        : "Сезон закрыт, итоги окончательные.";
        return new FinaleView(range.name(), current, form.weeks().size(), form.lines().size(),
                nominations, form, note);
    }

    private void champion(FormView form, List<Nomination> out) {
        int best = form.lines().get(0).total();
        List<String> who = new ArrayList<>();
        for (FormLine line : form.lines()) {
            if (line.total() == best) {
                who.add(line.username());
            }
        }
        int rounds = 0;
        for (Integer scored : form.lines().get(0).points()) {
            if (scored != null) {
                rounds++;
            }
        }
        out.add(new Nomination(who.size() > 1 ? "Чемпионы сезона" : "Чемпион сезона",
                String.join(", ", who),
                best + " " + pointWord(best) + " за " + rounds + " " + roundWord(rounds)));
    }

    private void bestRound(List<RoundResult> rows, Map<Long, UserLabel> names,
                           List<Nomination> out) {
        RoundResult best = null;
        for (RoundResult r : rows) {
            if (best == null || r.getPoints() > best.getPoints()) {
                best = r;
            }
        }
        if (best == null || best.getPoints() <= 0) {
            return;
        }
        List<String> who = new ArrayList<>();
        for (RoundResult r : rows) {
            if (r.getPoints() == best.getPoints() && r.getRoundWeek().equals(best.getRoundWeek())) {
                who.add(names.getOrDefault(r.getUserId(), UserLabel.UNKNOWN).username());
            }
        }
        out.add(new Nomination("Лучший тур", String.join(", ", who),
                best.getPoints() + " " + pointWord(best.getPoints())
                        + " в туре " + best.getRoundWeek().format(DAY)));
    }

    private void sharpestEye(List<RoundResult> rows, Map<Long, UserLabel> names,
                             List<Nomination> out) {
        Map<Long, Integer> exact = new HashMap<>();
        for (RoundResult r : rows) {
            exact.merge(r.getUserId(), r.getExactScores(), Integer::sum);
        }
        int best = 0;
        for (Integer count : exact.values()) {
            best = Math.max(best, count);
        }
        if (best <= 0) {
            return;
        }
        List<String> who = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : exact.entrySet()) {
            if (e.getValue() == best) {
                who.add(names.getOrDefault(e.getKey(), UserLabel.UNKNOWN).username());
            }
        }
        who.sort(Comparator.naturalOrder());
        out.add(new Nomination("Точный глаз", String.join(", ", who),
                best + " " + exactWord(best)));
    }

    /** Самая длинная череда выигранных туров подряд. Пропущенный тур череду не рвёт. */
    private void longestStreak(List<RoundResult> rows, Map<Long, UserLabel> names,
                               List<Nomination> out) {
        Map<Long, List<RoundResult>> byUser = new HashMap<>();
        for (RoundResult r : rows) {
            byUser.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
        }
        int best = 0;
        List<String> who = new ArrayList<>();
        for (Map.Entry<Long, List<RoundResult>> e : byUser.entrySet()) {
            List<RoundResult> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparing(RoundResult::getRoundWeek));
            List<Boolean> won = new ArrayList<>(list.size());
            for (RoundResult r : list) {
                won.add(r.isWinner());
            }
            int longest = longestWinStreak(won);
            if (longest > best) {
                best = longest;
                who = new ArrayList<>();
            }
            if (longest == best && longest > 0) {
                who.add(names.getOrDefault(e.getKey(), UserLabel.UNKNOWN).username());
            }
        }
        if (best < 2) {
            return; // одна победа — это не серия
        }
        who.sort(Comparator.naturalOrder());
        out.add(new Nomination("Серия побед", String.join(", ", who),
                best + " " + roundWord(best) + " подряд"));
    }

    /** Самая длинная череда подряд выигранных туров в списке «выиграл — не выиграл». */
    static int longestWinStreak(List<Boolean> wonInOrder) {
        int streak = 0;
        int longest = 0;
        for (Boolean won : wonInOrder) {
            streak = Boolean.TRUE.equals(won) ? streak + 1 : 0;
            longest = Math.max(longest, streak);
        }
        return longest;
    }

    // ------------------------------------------------------------------ мелочи

    private List<RoundResult> inRange(Long groupId, SeasonService.Range range) {
        List<RoundResult> rows = new ArrayList<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            if (range.from() != null && r.getRoundWeek().isBefore(range.from())) {
                continue;
            }
            if (range.to() != null && r.getRoundWeek().isAfter(range.to())) {
                continue;
            }
            rows.add(r);
        }
        return rows;
    }

    private Map<Long, UserLabel> names(Long groupId) {
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        return names;
    }

    static String pointWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "балл";
        }
        if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14)) {
            return "балла";
        }
        return "баллов";
    }

    static String roundWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "тур";
        }
        if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14)) {
            return "тура";
        }
        return "туров";
    }

    static String exactWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "точный счёт";
        }
        if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14)) {
            return "точных счёта";
        }
        return "точных счетов";
    }
}
