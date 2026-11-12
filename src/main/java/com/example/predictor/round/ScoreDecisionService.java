package com.example.predictor.round;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.domain.Prediction;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.domain.ScoreDecision;
import com.example.predictor.domain.ScoreDecisionVote;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.group.RulesService;
import com.example.predictor.group.RulesService.Rules;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.repository.ScoreDecisionRepository;
import com.example.predictor.repository.ScoreDecisionVoteRepository;
import com.example.predictor.round.MatchResolution.DecisionState;
import com.example.predictor.round.MatchResolution.Kind;
import com.example.predictor.round.RoundDtos.ScoreDecisionView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Матчи, по которым нет данных о добавочном времени или пенальти, хотя группа считает счёт с ними.
 * Владелец группы решает: предложить посчитать такой матч только по основному времени (все остальные
 * участники тура должны согласиться) или не учитывать матч. Если данные поступят от football-data.org,
 * решение теряет смысл: матч считается по правилам группы.
 */
@Service
public class ScoreDecisionService {

    /** Как учитывается один матч. basis: счёт, по которому считаются баллы (если матч идёт в зачёт). */
    public record Info(Kind kind, ScoreBasis basis, List<String> missing, DecisionState state,
                       boolean ownerDeclined, Set<Long> required, Map<Long, Boolean> votes,
                       Instant votingEndsAt) {
    }

    private final GroupAccess access;
    private final RulesService rulesService;
    private final MatchRepository matches;
    private final PredictionRepository predictions;
    private final GroupMemberRepository members;
    private final RoundResultRepository results;
    private final ScoreDecisionRepository decisions;
    private final ScoreDecisionVoteRepository votes;

    public ScoreDecisionService(GroupAccess access, RulesService rulesService, MatchRepository matches,
                                PredictionRepository predictions, GroupMemberRepository members,
                                RoundResultRepository results, ScoreDecisionRepository decisions,
                                ScoreDecisionVoteRepository votes) {
        this.access = access;
        this.rulesService = rulesService;
        this.matches = matches;
        this.predictions = predictions;
        this.members = members;
        this.results = results;
        this.decisions = decisions;
        this.votes = votes;
    }

    // ------------------------------------------------------------------ как учитывается матч

    /** Как учитывается каждый матч тура в этой группе. predictors: кто сделал прогнозы на тур. */
    public Map<Long, Info> resolve(Long groupId, Rules rules, List<FootballMatch> weekMatches,
                                   List<GroupMember> memberList, Set<Long> predictors) {
        Set<Long> memberIds = new HashSet<>();
        Long ownerId = null;
        for (GroupMember gm : memberList) {
            memberIds.add(gm.getUser().getId());
            if (gm.getRole() == GroupRole.OWNER) {
                ownerId = gm.getUser().getId();
            }
        }
        Set<Long> required = new HashSet<>(predictors);
        required.retainAll(memberIds);
        if (ownerId != null) {
            required.remove(ownerId);
        }

        ScoreBasis basis = rules.scoreBasis();
        Instant now = Instant.now();
        List<Long> needIds = new ArrayList<>();
        for (FootballMatch m : weekMatches) {
            if (!m.missingParts(basis).isEmpty()) {
                needIds.add(m.getId());
            }
        }
        Map<Long, ScoreDecision> decisionByMatch = new HashMap<>();
        Map<Long, Map<Long, Boolean>> votesByDecision = new HashMap<>();
        if (!needIds.isEmpty()) {
            for (ScoreDecision d : decisions.findByGroupIdAndMatchIdIn(groupId, needIds)) {
                decisionByMatch.put(d.getMatchId(), d);
            }
            if (!decisionByMatch.isEmpty()) {
                List<Long> decisionIds = decisionByMatch.values().stream().map(ScoreDecision::getId).toList();
                for (ScoreDecisionVote v : votes.findByDecisionIdIn(decisionIds)) {
                    votesByDecision.computeIfAbsent(v.getDecisionId(), k -> new HashMap<>())
                            .put(v.getUserId(), v.isAgree());
                }
            }
        }

        Map<Long, Info> result = new HashMap<>();
        for (FootballMatch m : weekMatches) {
            List<String> missing = m.missingParts(basis);
            ScoreDecision d = decisionByMatch.get(m.getId());
            Map<Long, Boolean> matchVotes = d == null ? Map.of() : votesByDecision.getOrDefault(d.getId(), Map.of());
            boolean ownerDeclined = d != null && d.getStatus() == ScoreDecision.Choice.DECLINED;
            Instant proposedAt = d == null ? now : d.getCreatedAt();
            DecisionState state = MatchResolution.decisionState(d != null, ownerDeclined, required, matchVotes,
                    proposedAt, now);
            Instant endsAt = d == null || ownerDeclined ? null : proposedAt.plus(MatchResolution.VOTING_PERIOD);
            Kind kind = MatchResolution.resolve(m.getStatus(), m.isFinished(), !missing.isEmpty(), state);
            ScoreBasis effective = kind == Kind.REGULAR_ONLY ? ScoreBasis.REGULAR_TIME : basis;
            result.put(m.getId(), new Info(kind, effective, missing, state, ownerDeclined, required, matchVotes, endsAt));
        }
        return result;
    }

    // ------------------------------------------------------------------ как показать

    /** Описание решения для экрана тура. null, если по матчу решать нечего. */
    public ScoreDecisionView toView(Info info, Long me, boolean iAmOwner, Map<Long, String> names) {
        if (info.missing().isEmpty() || info.kind() == Kind.FULL || info.kind() == Kind.NOT_PLAYED
                || info.kind() == Kind.CANCELLED) {
            return null;
        }
        List<String> agreed = new ArrayList<>();
        List<String> disagreed = new ArrayList<>();
        List<String> waiting = new ArrayList<>();
        for (Long userId : info.required()) {
            String name = names.getOrDefault(userId, "?");
            Boolean vote = info.votes().get(userId);
            if (vote == null) {
                waiting.add(name);
            } else if (vote) {
                agreed.add(name);
            } else {
                disagreed.add(name);
            }
        }
        agreed.sort(String::compareTo);
        disagreed.sort(String::compareTo);
        waiting.sort(String::compareTo);

        boolean canPropose = iAmOwner && info.kind() == Kind.NEEDS_DECISION;
        boolean canVote = !iAmOwner && info.kind() == Kind.VOTING
                && info.required().contains(me) && !info.votes().containsKey(me);
        Boolean myVote = info.votes().get(me);
        String what = whatIsMissing(info.missing());
        String state;
        String message;
        switch (info.kind()) {
            case NEEDS_DECISION -> {
                state = "NEEDS_DECISION";
                message = iAmOwner
                        ? "Нет данных о " + what + " этого матча. Вы можете предложить посчитать его только по "
                        + "основному времени (потребуется согласие всех участников тура) или не учитывать матч. "
                        + "Пока решения нет, итоги тура не подводятся."
                        : "Нет данных о " + what + " этого матча. Ждём решения владельца группы.";
            }
            case VOTING -> {
                state = "VOTING";
                message = canVote
                        ? "Владелец предлагает посчитать матч только по основному времени, потому что нет данных о "
                        + what + ". Вы согласны?"
                        : "Владелец предложил посчитать матч только по основному времени (нет данных о " + what
                        + "). Согласились " + agreed.size() + " из " + info.required().size()
                        + ". Если за сутки согласятся не все, матч не будет учтён.";
            }
            case REGULAR_ONLY -> {
                state = "ACCEPTED";
                message = "Матч считается только по основному времени: владелец предложил, все участники согласились.";
            }
            default -> {
                state = "DECLINED";
                message = info.ownerDeclined()
                        ? "Владелец не согласен считать матч только по основному времени: матч не учитывается."
                        : info.state() == DecisionState.EXPIRED
                        ? "За сутки с предложения согласились не все участники: матч не учитывается."
                        : "Кто-то из участников не согласился считать матч только по основному времени: "
                        + "матч не учитывается.";
            }
        }
        return new ScoreDecisionView(state, info.missing(), canPropose, canVote, myVote,
                agreed, disagreed, waiting,
                info.kind() == Kind.VOTING ? info.votingEndsAt() : null, message);
    }

    private static String whatIsMissing(List<String> missing) {
        boolean extra = missing.contains("EXTRA_TIME");
        boolean pens = missing.contains("PENALTIES");
        if (extra && pens) {
            return "добавочном времени и пенальти";
        }
        return pens ? "серии пенальти" : "добавочном времени";
    }

    // ------------------------------------------------------------------ действия

    /** Решение владельца: предложить счёт по основному времени (true) или не учитывать матч (false). */
    @Transactional
    public ScoreDecisionView propose(AppUser user, Long groupId, Long matchId, boolean regularTimeOnly) {
        Membership ms = access.requireOwner(user, groupId);
        Context c = context(ms, matchId);
        if (c.info().kind() != Kind.NEEDS_DECISION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, notNeededMessage(c.info()));
        }
        ScoreDecision d = new ScoreDecision(groupId, matchId,
                regularTimeOnly ? ScoreDecision.Choice.PROPOSED : ScoreDecision.Choice.DECLINED,
                user.getId(), Instant.now());
        try {
            decisions.saveAndFlush(d);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Решение по этому матчу уже принято");
        }
        return reload(ms, user, matchId);
    }

    /** Голос участника тура по предложению владельца. Голос окончательный. */
    @Transactional
    public ScoreDecisionView vote(AppUser user, Long groupId, Long matchId, boolean agree) {
        Membership ms = access.require(user, groupId);
        if (ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Владелец выражает своё решение предложением: голосовать должны остальные участники");
        }
        Context c = context(ms, matchId);
        Info info = c.info();
        if (info.kind() != Kind.VOTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    info.kind() == Kind.NEEDS_DECISION
                            ? "Владелец группы ещё не внёс предложение по этому матчу"
                            : notNeededMessage(info));
        }
        if (!info.required().contains(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Голосуют участники тура, сделавшие прогнозы на эту неделю");
        }
        if (info.votes().containsKey(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вы уже проголосовали: голос изменить нельзя");
        }
        ScoreDecision d = decisions.findByGroupIdAndMatchIdIn(groupId, List.of(matchId)).get(0);
        try {
            votes.saveAndFlush(new ScoreDecisionVote(d.getId(), user.getId(), agree, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вы уже проголосовали: голос изменить нельзя");
        }
        return reload(ms, user, matchId);
    }

    private record Context(FootballMatch match, Info info, Map<Long, String> names) {
    }

    private Context context(Membership ms, Long matchId) {
        FootballMatch match = matches.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Матч не найден"));
        FriendGroup group = ms.group();
        if (results.existsByGroupIdAndRoundWeek(group.getId(), match.getRoundWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Итоги этого тура уже подведены");
        }
        Rules rules = rulesService.rulesFor(group, match.getRoundWeek());
        List<GroupMember> memberList = members.findAllWithUser(group.getId());
        Set<Long> predictors = new HashSet<>();
        for (Prediction p : predictions.findForRound(group.getId(), match.getRoundWeek())) {
            predictors.add(p.getUser().getId());
        }
        Map<Long, String> names = new HashMap<>();
        for (GroupMember gm : memberList) {
            names.put(gm.getUser().getId(), gm.getUser().getUsername());
        }
        Info info = resolve(group.getId(), rules, List.of(match), memberList, predictors).get(matchId);
        return new Context(match, info, names);
    }

    private ScoreDecisionView reload(Membership ms, AppUser user, Long matchId) {
        Context c = context(ms, matchId);
        return toView(c.info(), user.getId(), ms.isOwner(), c.names());
    }

    private static String notNeededMessage(Info info) {
        return switch (info.kind()) {
            case FULL -> "Для этого матча решение не нужно: данных достаточно";
            case NOT_PLAYED, CANCELLED -> "Матч ещё не сыгран или отменён";
            case VOTING -> "Предложение по этому матчу уже внесено, идёт голосование";
            default -> "Решение по этому матчу уже принято";
        };
    }

    /** Нужно ли ещё какое-то решение по матчам тура: для сводки на экране. */
    public static int countPending(Collection<Info> infos) {
        int n = 0;
        for (Info i : infos) {
            if (i.kind().awaitingDecision()) {
                n++;
            }
        }
        return n;
    }
}
