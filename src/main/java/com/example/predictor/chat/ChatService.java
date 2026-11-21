package com.example.predictor.chat;

import com.example.predictor.common.UserLabel;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupChatRead;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupMessage;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.notify.DiscussionAlerts;
import com.example.predictor.repository.GroupChatReadRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.GroupMessageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Чат группы: разговор, не привязанный к туру.
 *
 * Обсуждение тура остаётся рядом с таблицей — там спорят о том, кто как угадал, и через
 * неделю этот спор уже никому не нужен. А договориться о встрече, скинуть ссылку или просто
 * поболтать удобнее в одном месте на всю группу: туры для этого сменяются слишком быстро.
 *
 * Лента отдаётся с конца: открыв чат, человек видит последние сообщения, а не первые за год.
 */
@Service
public class ChatService {

    /** mine=true: это сообщение написал тот, кто смотрит; canDelete — может ли он его убрать. */
    public record MessageDto(Long id, Long userId, String username, String badge, String text,
                             Instant createdAt, boolean mine, boolean deleted, boolean canDelete) {
    }

    /** unread — сколько сообщений пришло с тех пор, как участник последний раз читал чат. */
    public record FeedDto(List<MessageDto> messages, long total, long unread, boolean canWrite) {
    }

    /** Длиннее тысячи знаков это уже не реплика, а письмо. */
    private static final int MAX_LENGTH = 1000;

    /** Сколько последних сообщений отдавать при открытии чата. */
    private static final int PAGE = 100;

    private final GroupAccess access;
    private final GroupMessageRepository messages;
    private final GroupChatReadRepository reads;
    private final GroupMemberRepository members;
    private final DiscussionAlerts alerts;

    public ChatService(GroupAccess access, GroupMessageRepository messages,
                       GroupChatReadRepository reads, GroupMemberRepository members,
                       DiscussionAlerts alerts) {
        this.access = access;
        this.messages = messages;
        this.reads = reads;
        this.members = members;
        this.alerts = alerts;
    }

    /**
     * Лента чата. Без afterId — последние сообщения, от старых к новым; с afterId — только то,
     * что появилось после, чтобы открытый экран мог дочитывать новое, не перезагружая всё.
     */
    @Transactional(readOnly = true)
    public FeedDto feed(AppUser user, Long groupId, Long afterId) {
        Membership ms = access.require(user, groupId);

        List<GroupMessage> list;
        if (afterId == null) {
            list = new ArrayList<>(messages.findByGroupIdOrderByIdDesc(groupId,
                    PageRequest.of(0, PAGE)));
            Collections.reverse(list);
        } else {
            list = messages.findByGroupIdAndIdGreaterThanOrderByIdAsc(groupId, afterId);
        }

        Map<Long, UserLabel> names = labels(groupId);
        List<MessageDto> dtos = new ArrayList<>(list.size());
        for (GroupMessage m : list) {
            dtos.add(toDto(m, user, ms.isOwner(), names));
        }
        return new FeedDto(dtos, messages.countByGroupIdAndDeletedAtIsNull(groupId),
                unread(groupId, user.getId()), true);
    }

    /** Сколько новых сообщений у участника: по этому числу на кнопке чата стоит значок. */
    @Transactional(readOnly = true)
    public long unread(Long groupId, Long userId) {
        long lastRead = reads.findByGroupIdAndUserId(groupId, userId)
                .map(GroupChatRead::getLastReadId)
                .orElse(0L);
        return messages.countByGroupIdAndIdGreaterThanAndDeletedAtIsNull(groupId, lastRead);
    }

    @Transactional
    public MessageDto write(AppUser user, Long groupId, String text) {
        Membership ms = access.require(user, groupId);
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сообщение пустое");
        }
        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH);
        }
        GroupMessage saved = messages.save(
                new GroupMessage(groupId, user.getId(), clean, Instant.now()));
        messages.flush();
        // своё сообщение прочитано сразу: иначе автор увидел бы значок новых у самого себя
        markRead(user, groupId, saved.getId());
        alerts.newChatMessage(ms.group(), user, saved.getId(), clean, saved.getCreatedAt());
        return toDto(saved, user, ms.isOwner(), labels(groupId));
    }

    /** Своё сообщение убирает автор, чужое — владелец группы. */
    @Transactional
    public MessageDto delete(AppUser user, Long groupId, Long messageId) {
        Membership ms = access.require(user, groupId);
        GroupMessage message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Сообщение не найдено"));
        if (!message.getGroupId().equals(groupId)) {
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

    /** Отмечает, что участник дочитал чат до этого сообщения. */
    @Transactional
    public long markRead(AppUser user, Long groupId, Long lastId) {
        access.require(user, groupId);
        Instant now = Instant.now();
        GroupChatRead read = reads.findByGroupIdAndUserId(groupId, user.getId()).orElse(null);
        if (read == null) {
            reads.save(new GroupChatRead(groupId, user.getId(), lastId == null ? 0L : lastId, now));
        } else {
            read.readUpTo(lastId, now);
            reads.save(read);
        }
        return unread(groupId, user.getId());
    }

    private Map<Long, UserLabel> labels(Long groupId) {
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        return names;
    }

    private static MessageDto toDto(GroupMessage m, AppUser me, boolean iAmOwner,
                                    Map<Long, UserLabel> names) {
        UserLabel who = names.getOrDefault(m.getUserId(), UserLabel.UNKNOWN);
        boolean mine = m.getUserId().equals(me.getId());
        return new MessageDto(m.getId(), m.getUserId(), who.username(), who.badge(),
                m.isDeleted() ? "сообщение убрано" : m.getText(),
                m.getCreatedAt(), mine, m.isDeleted(),
                !m.isDeleted() && (mine || iAmOwner));
    }
}
