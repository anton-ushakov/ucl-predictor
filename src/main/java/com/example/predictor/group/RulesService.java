package com.example.predictor.group;

import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.RevealMode;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.domain.SubmissionMode;
import com.example.predictor.domain.Tiebreaker;
import com.example.predictor.repository.RoundRulesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Правила тура для группы. Пока в группе никто не сохранил прогноз на тур, действуют текущие
 * настройки группы. После первого сохранённого прогноза правила тура фиксируются и дальнейшие
 * изменения настроек на этот тур не влияют: нельзя поменять условия, когда игра уже началась.
 */
@Service
public class RulesService {

    public record Rules(int lockMinutesBefore, List<Tiebreaker> tiebreakers, RevealMode revealMode,
                        ScoreBasis scoreBasis, SubmissionMode submissionMode) {

        /** Момент, начиная с которого прогноз на матч принять уже нельзя. */
        public Instant lockAt(Instant kickoff) {
            return kickoff.minus(Duration.ofMinutes(lockMinutesBefore));
        }
    }

    private final RoundRulesRepository roundRules;

    public RulesService(RoundRulesRepository roundRules) {
        this.roundRules = roundRules;
    }

    public Rules current(FriendGroup group) {
        return new Rules(group.getLockMinutesBefore(), group.tiebreakerList(), group.getRevealMode(),
                group.scoreBasis(), group.getSubmissionMode());
    }

    @Transactional(readOnly = true)
    public Rules rulesFor(FriendGroup group, LocalDate week) {
        return roundRules.findByGroupIdAndRoundWeek(group.getId(), week)
                .map(r -> new Rules(r.getLockMinutesBefore(),
                        Tiebreaker.parseList(r.getTiebreakers()), r.getRevealMode(), r.scoreBasis(),
                        r.getSubmissionMode()))
                .orElseGet(() -> current(group));
    }

    /** Зафиксированы ли правила тура (то есть кто-то в группе уже сохранил прогноз). */
    @Transactional(readOnly = true)
    public boolean isFrozen(Long groupId, LocalDate week) {
        return roundRules.findByGroupIdAndRoundWeek(groupId, week).isPresent();
    }

    /** Фиксирует текущие настройки группы как правила тура, если они ещё не зафиксированы. */
    @Transactional
    public void freezeIfAbsent(FriendGroup group, LocalDate week) {
        ScoreBasis basis = group.scoreBasis();
        roundRules.freeze(group.getId(), week, group.getLockMinutesBefore(),
                group.getTiebreakers(), group.getRevealMode().name(),
                basis.extraTime(), basis.penalties(), group.getSubmissionMode().name());
    }
}
