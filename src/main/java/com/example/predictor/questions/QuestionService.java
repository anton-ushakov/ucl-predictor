package com.example.predictor.questions;

import com.example.predictor.common.AppTime;
import com.example.predictor.common.UserLabel;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.QuestionAnswer;
import com.example.predictor.domain.TournamentQuestion;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.QuestionAnswerRepository;
import com.example.predictor.repository.TournamentQuestionRepository;
import com.example.predictor.standings.RoundWeeks;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Вопросы на турнир: кто выиграет Лигу чемпионов, кто станет лучшим бомбардиром.
 * Отвечают один раз заранее, до закрытия ответ можно переписать. После закрытия ответы
 * видны всем — в этом половина удовольствия, — а правильные отмечает владелец группы.
 *
 * <p>Проверку делает человек, а не сравнение строк: «Реал», «Реал Мадрид» и «Real Madrid» —
 * один и тот же ответ, и спорить об этом с программой было бы глупо.
 */
@Service
public class QuestionService {

    /** mine — мой ответ, answers — чужие (видны после закрытия). */
    public record AnswerDto(Long userId, String username, String badge, String answer,
                            Boolean correct) {
    }

    public record QuestionDto(Long id, String title, String hint, int points, Instant closesAt,
                              boolean open, boolean settled, String correctAnswer, String mine,
                              Boolean myCorrect, int answered, int memberCount,
                              List<AnswerDto> answers) {
    }

    public record QuestionsView(List<QuestionDto> questions, boolean canManage, String note) {
    }

    private final GroupAccess access;
    private final TournamentQuestionRepository questions;
    private final QuestionAnswerRepository answers;
    private final GroupMemberRepository members;
    private final AppTime time;

    public QuestionService(GroupAccess access, TournamentQuestionRepository questions,
                           QuestionAnswerRepository answers, GroupMemberRepository members,
                           AppTime time) {
        this.access = access;
        this.questions = questions;
        this.answers = answers;
        this.members = members;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public QuestionsView view(AppUser user, Long groupId) {
        Membership ms = access.require(user, groupId);
        List<TournamentQuestion> list = questions.findByGroupIdOrderByClosesAtDescIdDesc(groupId);
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        int memberCount = names.size();

        Map<Long, List<QuestionAnswer>> byQuestion = new HashMap<>();
        if (!list.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (TournamentQuestion q : list) {
                ids.add(q.getId());
            }
            for (QuestionAnswer a : answers.findByQuestionIdIn(ids)) {
                byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
            }
        }

        Instant now = Instant.now();
        List<QuestionDto> dtos = new ArrayList<>();
        for (TournamentQuestion q : list) {
            List<QuestionAnswer> all = byQuestion.getOrDefault(q.getId(), List.of());
            boolean open = q.isOpen(now);
            QuestionAnswer mine = null;
            for (QuestionAnswer a : all) {
                if (a.getUserId().equals(user.getId())) {
                    mine = a;
                }
            }
            // пока приём открыт, чужие ответы не показываем: иначе все напишут одно и то же
            List<AnswerDto> visible = new ArrayList<>();
            if (!open) {
                List<QuestionAnswer> sorted = new ArrayList<>(all);
                sorted.sort(Comparator.comparing(QuestionAnswer::getCreatedAt));
                for (QuestionAnswer a : sorted) {
                    UserLabel who = names.getOrDefault(a.getUserId(), UserLabel.UNKNOWN);
                    visible.add(new AnswerDto(a.getUserId(), who.username(), who.badge(),
                            a.getAnswer(), a.getCorrect()));
                }
            }
            dtos.add(new QuestionDto(q.getId(), q.getTitle(), q.getHint(), q.getPoints(),
                    q.getClosesAt(), open, q.isSettled(), q.getCorrectAnswer(),
                    mine == null ? null : mine.getAnswer(),
                    mine == null ? null : mine.getCorrect(),
                    all.size(), memberCount, visible));
        }
        String note = dtos.isEmpty()
                ? "Вопросов пока нет. Владелец группы может спросить, кто выиграет турнир "
                        + "или кто станет лучшим бомбардиром."
                : "Ответы видны всем после закрытия приёма. Баллы за правильные идут в общий зачёт.";
        return new QuestionsView(dtos, ms.isOwner(), note);
    }

    @Transactional
    public QuestionsView create(AppUser user, Long groupId, String title, String hint, int points,
                                Instant closesAt) {
        access.requireOwner(user, groupId);
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isBlank() || cleanTitle.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Напишите вопрос");
        }
        if (points < 1 || points > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Баллы за ответ: от 1 до 100");
        }
        if (closesAt == null || closesAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Приём ответов должен закрываться в будущем");
        }
        String cleanHint = hint == null || hint.isBlank() ? null
                : hint.trim().substring(0, Math.min(hint.trim().length(), 300));
        LocalDate week = RoundWeeks.mondayOf(closesAt, time.zone());
        questions.save(new TournamentQuestion(groupId, cleanTitle, cleanHint, points, closesAt,
                week, Instant.now(), user.getId()));
        return view(user, groupId);
    }

    @Transactional
    public QuestionsView answer(AppUser user, Long groupId, Long questionId, String text) {
        access.require(user, groupId);
        TournamentQuestion question = require(groupId, questionId);
        String clean = text == null ? "" : text.trim();
        if (clean.isBlank() || clean.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Напишите ответ");
        }
        Instant now = Instant.now();
        if (!question.isOpen(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Приём ответов на этот вопрос закрыт");
        }
        QuestionAnswer existing = answers.findByQuestionIdAndUserId(questionId, user.getId())
                .orElse(null);
        if (existing == null) {
            answers.save(new QuestionAnswer(questionId, user.getId(), clean, now));
        } else {
            existing.change(clean, now);
            answers.save(existing);
        }
        return view(user, groupId);
    }

    /**
     * Владелец подводит итог: пишет правильный ответ и отмечает, кто угадал.
     * Отмечать можно и после — например, если сначала ошиблись.
     */
    @Transactional
    public QuestionsView settle(AppUser user, Long groupId, Long questionId, String correctAnswer,
                                List<Long> correctUserIds) {
        access.requireOwner(user, groupId);
        TournamentQuestion question = require(groupId, questionId);
        Set<Long> winners = new HashSet<>(correctUserIds == null ? List.of() : correctUserIds);
        for (QuestionAnswer a : answers.findByQuestionId(questionId)) {
            a.mark(winners.contains(a.getUserId()));
            answers.save(a);
        }
        String clean = correctAnswer == null ? null : correctAnswer.trim();
        if (clean != null && clean.length() > 200) {
            clean = clean.substring(0, 200);
        }
        question.settle(clean, Instant.now());
        questions.save(question);
        return view(user, groupId);
    }

    @Transactional
    public QuestionsView remove(AppUser user, Long groupId, Long questionId) {
        access.requireOwner(user, groupId);
        questions.delete(require(groupId, questionId));
        return view(user, groupId);
    }

    /** Заработанные баллы по вопросам: userId → сумма. Нужны общему зачёту. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> bonusPoints(Long groupId, LocalDate from, LocalDate to) {
        Map<Long, Integer> bonus = new HashMap<>();
        List<TournamentQuestion> list = questions.findByGroupIdOrderByClosesAtDescIdDesc(groupId);
        Map<Long, Integer> pointsOf = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (TournamentQuestion q : list) {
            if (!q.isSettled()) {
                continue;
            }
            if (from != null && q.getRoundWeek().isBefore(from)) {
                continue;
            }
            if (to != null && q.getRoundWeek().isAfter(to)) {
                continue;
            }
            pointsOf.put(q.getId(), q.getPoints());
            ids.add(q.getId());
        }
        if (ids.isEmpty()) {
            return bonus;
        }
        for (QuestionAnswer a : answers.findByQuestionIdIn(ids)) {
            if (a.isCorrect()) {
                bonus.merge(a.getUserId(), pointsOf.getOrDefault(a.getQuestionId(), 0), Integer::sum);
            }
        }
        return bonus;
    }

    /**
     * То же самое, но с разбивкой по турам: неделя → (userId → баллы). Нужно графику формы,
     * иначе линия расходилась бы с таблицей зачёта там, где были вопросы.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Map<Long, Integer>> bonusByWeek(Long groupId, LocalDate from, LocalDate to) {
        Map<Long, LocalDate> weekOf = new HashMap<>();
        Map<Long, Integer> pointsOf = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (TournamentQuestion q : questions.findByGroupIdOrderByClosesAtDescIdDesc(groupId)) {
            if (!q.isSettled()) {
                continue;
            }
            if (from != null && q.getRoundWeek().isBefore(from)) {
                continue;
            }
            if (to != null && q.getRoundWeek().isAfter(to)) {
                continue;
            }
            weekOf.put(q.getId(), q.getRoundWeek());
            pointsOf.put(q.getId(), q.getPoints());
            ids.add(q.getId());
        }
        Map<LocalDate, Map<Long, Integer>> byWeek = new HashMap<>();
        if (ids.isEmpty()) {
            return byWeek;
        }
        for (QuestionAnswer a : answers.findByQuestionIdIn(ids)) {
            if (!a.isCorrect()) {
                continue;
            }
            LocalDate week = weekOf.get(a.getQuestionId());
            if (week == null) {
                continue;
            }
            byWeek.computeIfAbsent(week, k -> new HashMap<>())
                    .merge(a.getUserId(), pointsOf.getOrDefault(a.getQuestionId(), 0), Integer::sum);
        }
        return byWeek;
    }

    private TournamentQuestion require(Long groupId, Long questionId) {
        TournamentQuestion question = questions.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вопрос не найден"));
        if (!question.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Вопрос не найден");
        }
        return question;
    }
}
