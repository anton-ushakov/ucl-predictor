package com.example.predictor.round;

import com.example.predictor.domain.Competition;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.GroupRound;
import com.example.predictor.repository.GroupRoundRepository;
import com.example.predictor.repository.RoundMatchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Туры, которые группа не собирает руками: они появляются сами и знают, из чего собраны.
 *
 * Тур Лиги чемпионов идёт со вторника по четверг, тур Англии — с субботы по понедельник, и в
 * одну календарную неделю их попадает два разных. Поэтому тур группы повторяет тур турнира:
 * «Лига чемпионов» — пятый тур Лиги чемпионов целиком, «Топ-матчи» — подборка к очередному
 * туру чемпионатов. Чтобы второй раз найти тот же тур и дополнить его, а не завести рядом
 * ещё один, у тура есть пометка источника: «CL:5», «TOP:14».
 */
@Service
public class AutoRounds {

    private final GroupRoundRepository rounds;
    private final RoundMatchRepository selection;

    public AutoRounds(GroupRoundRepository rounds, RoundMatchRepository selection) {
        this.rounds = rounds;
        this.selection = selection;
    }

    /** Пометка источника для тура турнира: «CL:5». Без номера тура — по стадии или дате. */
    public static String sourceKeyOf(Competition competition, FootballMatch match, ZoneId zone) {
        String code = competition == null || competition.getCode() == null
                ? "X" : competition.getCode();
        if (match.getMatchday() != null) {
            return code + ":" + match.getMatchday();
        }
        if (match.getStage() != null && !match.getStage().isBlank()) {
            return code + ":" + match.getStage();
        }
        // ни номера тура, ни стадии: кубковый матч — пусть тур будет на день матча
        return code + ":d" + LocalDate.ofInstant(match.getKickoffAt(), zone);
    }

    /**
     * Находит тур с такой пометкой или создаёт его. Название ставится только новому туру:
     * переименованный владельцем тур автосостав не перебивает.
     *
     * @param anchor желаемый день для ключа; если он занят, берётся следующий свободный
     */
    public GroupRound findOrCreate(Long groupId, String sourceKey, String title, LocalDate anchor,
                                   Long createdBy, Instant now) {
        GroupRound existing = rounds.findByGroupIdAndSourceKey(groupId, sourceKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDate key = freeKey(groupId, anchor);
        return rounds.save(new GroupRound(groupId, key, title, false, now, createdBy, sourceKey));
    }

    /**
     * Свободный день для ключа. Ключ — служебная метка: важно лишь, чтобы он не совпал с
     * другим туром этой группы. Начинаем с дня первого матча — так ключи остаются
     * осмысленными, если однажды придётся смотреть в базу руками.
     */
    private LocalDate freeKey(Long groupId, LocalDate anchor) {
        LocalDate day = anchor;
        for (int i = 0; i < 400; i++) {
            if (!rounds.existsByGroupIdAndRoundKey(groupId, day)
                    && selection.findByGroupIdAndRoundWeek(groupId, day).isEmpty()) {
                return day;
            }
            day = day.plusDays(1);
        }
        throw new IllegalStateException("Не нашлось свободного дня для тура группы " + groupId);
    }
}
