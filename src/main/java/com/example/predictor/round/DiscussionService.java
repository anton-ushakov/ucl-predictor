package com.example.predictor.round;

import com.example.predictor.common.UserLabel;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.RoundMessage;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.notify.DiscussionAlerts;
import com.example.predictor.repository.RoundMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обсуждение тура: лента сообщений рядом с таблицей. Разговор привязан к туру намеренно —
 * спор о том, кто как угадал, идёт там, где видно результаты, и не смешивается с болтовнёй
 * за прошлый месяц.
 */
@Service
public class DiscussionService {

    /** mine=true: это сообщение написал тот, кто сейчас смотрит; canDelete — может ли он его убрать. */
    public record MessageDto(Long id, Long userId, String username, String badge, String text,
                             Instant createdAt, boolean mine, boolean deleted, boolean canDelete) {
    }

    public record FeedDto(LocalDate week, List<MessageDto> messages, long total, boolean canWrite) {
    }

    /** Длиннее тысячи знаков это уже не реплика, а письмо. */
    private static final int MAX_LENGTH = 1000;

    private final GroupAccess access;
    private final RoundMessageRepository messages;
    private final GroupMemberRepository members;
    private final DiscussionAlerts alerts;

    public DiscussionService(GroupAccess access, RoundMessageRepository messages,
                             GroupMemberRepository members, DiscussionAlerts alerts) {
        this.access = access;
        this.messages = messages;
        this.members = members;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public FeedDto feed(AppUser user, Long groupId, LocalDate week, Long afterId) {
        Membership ms = access.require(user, groupId);

        List<RoundMessage> list = afterId == null
                ? messages.findByGroupIdAndRoundWeekOrderByIdAsc(groupId, week)
                : messages.findByGroupIdAndRoundWeekAndIdGreaterThanOrderByIdAsc(groupId, week, afterId);

        Map<Long, UserLabel> names = labels(groupId);
        List<MessageDto> dtos = new ArrayList<>(list.size());
        for (RoundMessage m : list) {
            dtos.add(toDto(m, user, ms.isOwner(), names));
        }
        return new FeedDto(week, dtos,
                messages.countByGroupIdAndRoundWeekAndDeletedAtIsNull(groupId, week), true);
    }

    @Transactional
    public MessageDto write(AppUser user, Long groupId, LocalDate week, String text) {
        Membership ms = access.require(user, groupId);
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сообщение пустое");
        }
        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH);
        }
        RoundMessage saved = messages.save(
                new RoundMessage(groupId, week, user.getId(), clean, Instant.now()));
        messages.flush();
        // остальные узнают сразу: разговор тем и живёт, что ответ приходит в ту же минуту
        alerts.newMessage(ms.group(), week, user, saved.getId(), clean, saved.getCreatedAt());
        return toDto(saved, user, ms.isOwner(), labels(groupId));
    }

    /**
     * Своё сообщение может убрать автор, чужое — владелец группы. Запись остаётся в базе
     * помеченной: в ленте видно, что здесь что-то было, и ответы на него не повисают.
     */
    @Transactional
    public MessageDto delete(AppUser user, Long groupId, LocalDate week, Long messageId) {
        Membership ms = access.require(user, groupId);
        RoundMessage message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Сообщение не найдено"));
        if (!message.getGroupId().equals(groupId) || !message.getRoundWeek().equals(week)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сообщение не найдено");
        }
        if (!message.getUserId().equals(user.getId()) && !ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Убрать чужое сообщение может только владелец группы");
        }
        message.delete(Instant.now());
        messages.save(message);
        return toDto(message, user, ms.isOwner(), labels(groupId));
    }

    private Map<Long, UserLabel> labels(Long groupId) {
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        return names;
    }

    private static MessageDto toDto(RoundMessage m, AppUser me, boolean iAmOwner,
                                    Map<Long, UserLabel> names) {
        UserLabel who = names.getOrDefault(m.getUserId(), UserLabel.UNKNOWN);
        boolean mine = m.getUserId().equals(me.getId());
        return new MessageDto(m.getId(), m.getUserId(), who.username(), who.badge(),
                m.isDeleted() ? "сообщение убрано" : m.getText(),
                m.getCreatedAt(), mine, m.isDeleted(),
                !m.isDeleted() && (mine || iAmOwner));
    }

}
