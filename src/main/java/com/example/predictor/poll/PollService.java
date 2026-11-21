package com.example.predictor.poll;

import com.example.predictor.common.UserLabel;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupPoll;
import com.example.predictor.domain.PollVote;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.notify.PollAlerts;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.GroupPollRepository;
import com.example.predictor.repository.PollVoteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Голосования группы: вопрос, на который отвечают «да» или «нет».
 *
 * Владелец решает не всё — где-то честнее спросить всех, поэтому вопрос может задать любой
 * участник. Голосование бывает открытым (после завершения видно, кто как ответил) и закрытым
 * (виден только итог). Кто как проголосовал, не показывается, пока голосование идёт, даже в
 * открытом: первые голоса тянули бы за собой остальные.
 */
@Service
public class PollService {

    /** Голос одного участника: показывается только в открытом и уже закрытом голосовании. */
    public record VoterDto(Long userId, String username, String badge, boolean yes) {
    }

    /**
     * Голосование целиком. myVote: true — «за», false — «против», null — ещё не голосовал.
     * yesPercent и noPercent считаются от числа проголосовавших, а не от всей группы:
     * молчание — это не голос.
     */
    public record PollDto(Long id, String question, String author, Long authorId,
                          boolean openVotes, boolean closed, Instant createdAt, Instant closesAt,
                          int yes, int no, int voted, int members,
                          int yesPercent, int noPercent,
                          Boolean myVote, boolean canClose, boolean canDelete,
                          List<VoterDto> voters, String note) {
    }

    public record PollsView(List<PollDto> polls, int waitingForMe, String note) {
    }

    private static final int MAX_QUESTION = 200;
    private static final int MAX_DAYS = 30;

    private final GroupAccess access;
    private final GroupPollRepository polls;
    private final PollVoteRepository votes;
    private final GroupMemberRepository members;
    private final PollAlerts alerts;

    public PollService(GroupAccess access, GroupPollRepository polls, PollVoteRepository votes,
                       GroupMemberRepository members, PollAlerts alerts) {
        this.access = access;
        this.polls = polls;
        this.votes = votes;
        this.members = members;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public PollsView list(AppUser user, Long groupId) {
        Membership ms = access.require(user, groupId);
        Instant now = Instant.now();
        List<GroupPoll> all = polls.findByGroupIdOrderByIdDesc(groupId);
        Map<Long, UserLabel> names = labels(groupId);
        int memberCount = names.size();

        Map<Long, List<PollVote>> byPoll = new HashMap<>();
        if (!all.isEmpty()) {
            for (PollVote v : votes.findByPollIdIn(all.stream().map(GroupPoll::getId).toList())) {
                byPoll.computeIfAbsent(v.getPollId(), k -> new ArrayList<>()).add(v);
            }
        }

        List<PollDto> dtos = new ArrayList<>(all.size());
        int waiting = 0;
        for (GroupPoll poll : all) {
            PollDto dto = toDto(poll, byPoll.getOrDefault(poll.getId(), List.of()), user, ms,
                    names, memberCount, now);
            if (!dto.closed() && dto.myVote() == null) {
                waiting++;
            }
            dtos.add(dto);
        }
        // идущие голосования впереди: решать нужно их, а прошлые уже история
        dtos.sort(Comparator.comparing(PollDto::closed).thenComparing(PollDto::id,
                Comparator.reverseOrder()));

        String note = dtos.isEmpty()
                ? "Голосований пока не было. Вопрос может задать любой участник — и владелец тут "
                        + "не главнее остальных."
                : "Проценты считаются от числа проголосовавших: кто промолчал, тот не в счёт.";
        return new PollsView(dtos, waiting, note);
    }

    /** Новый вопрос. days — через сколько дней закрыть само; 0 или null — пока не закроют руками. */
    @Transactional
    public PollDto create(AppUser user, Long groupId, String question, boolean openVotes,
                          Integer days) {
        Membership ms = access.require(user, groupId);
        String clean = question == null ? "" : question.trim();
        if (clean.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Вопрос слишком короткий: напишите, о чём голосуем");
        }
        if (clean.length() > MAX_QUESTION) {
            clean = clean.substring(0, MAX_QUESTION);
        }
        if (days != null && (days < 0 || days > MAX_DAYS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Срок голосования — от одного дня до " + MAX_DAYS);
        }
        Instant now = Instant.now();
        Instant closesAt = days == null || days == 0 ? null : now.plus(Duration.ofDays(days));

        GroupPoll poll = polls.save(
                new GroupPoll(groupId, user.getId(), clean, openVotes, now, closesAt));
        polls.flush();
        alerts.newPoll(ms.group(), user, poll.getId(), clean, now);

        Map<Long, UserLabel> names = labels(groupId);
        return toDto(poll, List.of(), user, ms, names, names.size(), now);
    }

    @Transactional
    public PollDto vote(AppUser user, Long groupId, Long pollId, boolean yes) {
        Membership ms = access.require(user, groupId);
        Instant now = Instant.now();
        GroupPoll poll = require(groupId, pollId);
        if (poll.isClosed(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Голосование закончилось, голос уже не изменить");
        }
        PollVote vote = votes.findByPollIdAndUserId(pollId, user.getId()).orElse(null);
        if (vote == null) {
            votes.save(new PollVote(pollId, user.getId(), yes, now));
        } else {
            vote.change(yes, now);
            votes.save(vote);
        }
        votes.flush();
        Map<Long, UserLabel> names = labels(groupId);
        return toDto(poll, votes.findByPollIdIn(List.of(pollId)), user, ms, names, names.size(),
                now);
    }

    /** Закрыть может тот, кто спросил, и владелец группы. */
    @Transactional
    public PollDto close(AppUser user, Long groupId, Long pollId) {
        Membership ms = access.require(user, groupId);
        Instant now = Instant.now();
        GroupPoll poll = require(groupId, pollId);
        if (!poll.getAuthorId().equals(user.getId()) && !ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Закрыть голосование может тот, кто его завёл, или владелец группы");
        }
        poll.close(now);
        polls.save(poll);
        Map<Long, UserLabel> names = labels(groupId);
        return toDto(poll, votes.findByPollIdIn(List.of(pollId)), user, ms, names, names.size(),
                now);
    }

    /** Убрать голосование целиком: автор или владелец. */
    @Transactional
    public void delete(AppUser user, Long groupId, Long pollId) {
        Membership ms = access.require(user, groupId);
        GroupPoll poll = require(groupId, pollId);
        if (!poll.getAuthorId().equals(user.getId()) && !ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Убрать голосование может тот, кто его завёл, или владелец группы");
        }
        polls.delete(poll);
    }

    /** Доля в процентах от проголосовавших: молчание — это не голос, поэтому делим на них. */
    static int percentOf(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }

    private GroupPoll require(Long groupId, Long pollId) {
        GroupPoll poll = polls.findById(pollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Голосование не найдено"));
        if (!poll.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Голосование не найдено");
        }
        return poll;
    }

    private PollDto toDto(GroupPoll poll, List<PollVote> pollVotes, AppUser me, Membership ms,
                          Map<Long, UserLabel> names, int memberCount, Instant now) {
        int yes = 0;
        Boolean mine = null;
        for (PollVote v : pollVotes) {
            if (v.isYes()) {
                yes++;
            }
            if (v.getUserId().equals(me.getId())) {
                mine = v.isYes();
            }
        }
        int voted = pollVotes.size();
        int no = voted - yes;
        boolean closed = poll.isClosed(now);

        int yesPercent = percentOf(yes, voted);
        int noPercent = voted == 0 ? 0 : 100 - yesPercent;

        List<VoterDto> voters = new ArrayList<>();
        if (closed && poll.isOpenVotes()) {
            for (PollVote v : pollVotes) {
                UserLabel who = names.getOrDefault(v.getUserId(), UserLabel.UNKNOWN);
                voters.add(new VoterDto(v.getUserId(), who.username(), who.badge(), v.isYes()));
            }
            voters.sort(Comparator.comparing(VoterDto::yes).reversed()
                    .thenComparing(VoterDto::username));
        }

        boolean mineOrOwner = poll.getAuthorId().equals(me.getId()) || ms.isOwner();
        UserLabel author = names.getOrDefault(poll.getAuthorId(), UserLabel.UNKNOWN);
        return new PollDto(poll.getId(), poll.getQuestion(), author.username(), poll.getAuthorId(),
                poll.isOpenVotes(), closed, poll.getCreatedAt(), poll.getClosesAt(),
                yes, no, voted, memberCount, yesPercent, noPercent, mine,
                !closed && mineOrOwner, mineOrOwner, voters, note(poll, closed, voted));
    }

    private static String note(GroupPoll poll, boolean closed, int voted) {
        if (!closed) {
            return poll.isOpenVotes()
                    ? "Открытое: когда голосование закончится, будет видно, кто как ответил."
                    : "Закрытое: кто как ответил, не увидит никто — только общий итог.";
        }
        if (voted == 0) {
            return "Голосование закончилось, а голосов так и не было.";
        }
        return poll.isOpenVotes() ? "Голосование закончилось." : "Закрытое голосование, только итог.";
    }

    private Map<Long, UserLabel> labels(Long groupId) {
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        return names;
    }
}
