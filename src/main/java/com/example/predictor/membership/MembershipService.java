package com.example.predictor.membership;

import com.example.predictor.common.AuthModeConfig;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRequest;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.domain.RequestKind;
import com.example.predictor.domain.RequestStatus;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.GroupRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Вступление в группу без кодов: владелец приглашает участника по имени, а желающий вступить
 * подаёт заявку, которую владелец подтверждает. Работает в простом режиме входа (app.auth.mode=SIMPLE).
 */
@Service
public class MembershipService {

    /** Группа, в которой я не состою. myState: null, PENDING_INVITE или PENDING_REQUEST. */
    public record OpenGroupDto(Long id, String name, String ownerName, int memberCount, String myState) {
    }

    /**
     * Приглашение или заявка. kind: INVITE (позвал владелец) или REQUEST (попросился участник).
     * canDecide: вам решать принять или отклонить. canCancel: вы это создали и можете отозвать.
     */
    public record RequestDto(Long id, Long groupId, String groupName, Long userId, String username,
                             String kind, String createdByName, Instant createdAt,
                             boolean canDecide, boolean canCancel, String message) {
    }

    /** Участник приложения глазами владельца группы: кого можно позвать, кто уже внутри. */
    public record CandidateDto(Long userId, String username, boolean member, boolean owner,
                               boolean pendingInvite, boolean pendingRequest) {
        /** Можно отметить галочкой для приглашения. */
        public boolean invitable() {
            return !member && !pendingInvite;
        }
    }

    private final AuthModeConfig authMode;
    private final GroupAccess access;
    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final AppUserRepository users;
    private final GroupRequestRepository requests;

    public MembershipService(AuthModeConfig authMode, GroupAccess access, FriendGroupRepository groups,
                             GroupMemberRepository members, AppUserRepository users,
                             GroupRequestRepository requests) {
        this.authMode = authMode;
        this.access = access;
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.requests = requests;
    }

    // ------------------------------------------------------------------ поиск групп

    /** Группы, в которых участник не состоит: чтобы было куда подать заявку. */
    @Transactional(readOnly = true)
    public List<OpenGroupDto> openGroups(AppUser current) {
        authMode.requireRequests();
        Set<Long> mine = new HashSet<>();
        for (GroupMember gm : members.findAllOfUser(current.getId())) {
            mine.add(gm.getGroup().getId());
        }
        Map<Long, RequestKind> pending = new HashMap<>();
        for (GroupRequest r : requests.findByUserIdAndStatus(current.getId(), RequestStatus.PENDING)) {
            pending.put(r.getGroupId(), r.getKind());
        }

        List<OpenGroupDto> result = new ArrayList<>();
        for (FriendGroup group : groups.findAll()) {
            if (mine.contains(group.getId())) {
                continue;
            }
            List<GroupMember> groupMembers = members.findAllWithUser(group.getId());
            String owner = groupMembers.stream()
                    .filter(gm -> gm.getRole() == GroupRole.OWNER)
                    .map(gm -> gm.getUser().getUsername())
                    .findFirst().orElse("?");
            RequestKind kind = pending.get(group.getId());
            String state = kind == null ? null
                    : kind == RequestKind.INVITE ? "PENDING_INVITE" : "PENDING_REQUEST";
            result.add(new OpenGroupDto(group.getId(), group.getName(), owner,
                    groupMembers.size(), state));
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    /** Все участники приложения с пометками: кто в группе, кого уже позвали, кто просится. */
    @Transactional(readOnly = true)
    public List<CandidateDto> candidates(AppUser current, Long groupId) {
        authMode.requireRequests();
        access.requireOwner(current, groupId);

        Map<Long, GroupRole> roles = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            roles.put(gm.getUser().getId(), gm.getRole());
        }
        Map<Long, RequestKind> pending = new HashMap<>();
        for (GroupRequest r : requests.findByGroupIdAndStatusOrderByCreatedAtDesc(
                groupId, RequestStatus.PENDING)) {
            pending.put(r.getUserId(), r.getKind());
        }

        List<CandidateDto> result = new ArrayList<>();
        for (AppUser user : users.findAll()) {
            GroupRole role = roles.get(user.getId());
            RequestKind kind = pending.get(user.getId());
            result.add(new CandidateDto(user.getId(), user.getUsername(),
                    role != null, role == GroupRole.OWNER,
                    kind == RequestKind.INVITE, kind == RequestKind.REQUEST));
        }
        result.sort((a, b) -> a.username().compareToIgnoreCase(b.username()));
        return result;
    }

    /**
     * Приглашает сразу нескольких участников. Тех, кто уже в группе или уже приглашён, пропускает:
     * так кнопка «позвать всех» не спотыкается на полпути.
     */
    @Transactional
    public List<RequestDto> inviteMany(AppUser current, Long groupId, List<Long> userIds) {
        authMode.requireRequests();
        FriendGroup group = access.requireOwner(current, groupId).group();

        Set<Long> inGroup = new HashSet<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            inGroup.add(gm.getUser().getId());
        }
        Set<Long> alreadyPending = new HashSet<>();
        for (GroupRequest r : requests.findByGroupIdAndStatusOrderByCreatedAtDesc(
                groupId, RequestStatus.PENDING)) {
            alreadyPending.add(r.getUserId());
        }

        List<RequestDto> created = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (inGroup.contains(userId) || alreadyPending.contains(userId)) {
                continue;
            }
            AppUser invitee = users.findById(userId).orElse(null);
            if (invitee == null) {
                continue;
            }
            GroupRequest saved = create(groupId, userId, RequestKind.INVITE, current.getId());
            created.add(toDto(saved, group.getName(), invitee.getUsername(),
                    current.getUsername(), current));
        }
        return created;
    }

    /**
     * Исключает участника из группы. Его прошлые прогнозы и результаты остаются в истории:
     * подведённые итоги не переписываются.
     */
    @Transactional
    public void removeMember(AppUser current, Long groupId, Long userId) {
        access.requireOwner(current, groupId);
        if (userId.equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Владелец не может исключить сам себя");
        }
        GroupMember member = members.findMembership(groupId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "В этой группе нет такого участника"));
        if (member.getRole() == GroupRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Владельца исключить нельзя");
        }
        members.delete(member);
    }

    // ------------------------------------------------------------------ создание

    /** Заявка на вступление: решение примет владелец группы. */
    @Transactional
    public RequestDto requestToJoin(AppUser current, Long groupId) {
        authMode.requireRequests();
        FriendGroup group = groups.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));
        if (members.findMembership(groupId, current.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вы уже в этой группе");
        }
        GroupRequest saved = create(groupId, current.getId(), RequestKind.REQUEST, current.getId());
        return toDto(saved, group.getName(), current.getUsername(), current.getUsername(), current);
    }

    /** Приглашение участника по имени: решение примет он сам. Доступно владельцу группы. */
    @Transactional
    public RequestDto invite(AppUser current, Long groupId, String username) {
        authMode.requireRequests();
        FriendGroup group = access.requireOwner(current, groupId).group();
        AppUser invitee = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Нет участника с таким именем"));
        if (invitee.getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Вы и так в этой группе");
        }
        if (members.findMembership(groupId, invitee.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Этот участник уже в группе");
        }
        GroupRequest saved = create(groupId, invitee.getId(), RequestKind.INVITE, current.getId());
        return toDto(saved, group.getName(), invitee.getUsername(), current.getUsername(), current);
    }

    private GroupRequest create(Long groupId, Long userId, RequestKind kind, Long createdBy) {
        requests.findByGroupIdAndUserIdAndStatus(groupId, userId, RequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            existing.getKind() == RequestKind.INVITE
                                    ? "Приглашение уже отправлено, ждём ответа"
                                    : "Заявка уже подана, ждём решения владельца");
                });
        try {
            return requests.saveAndFlush(
                    new GroupRequest(groupId, userId, kind, createdBy, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Такая запись уже есть");
        }
    }

    // ------------------------------------------------------------------ списки

    /** Приглашения, адресованные мне. */
    @Transactional(readOnly = true)
    public List<RequestDto> myInvitations(AppUser current) {
        authMode.requireRequests();
        List<RequestDto> result = new ArrayList<>();
        for (GroupRequest r : requests.findByUserIdAndKindAndStatusOrderByCreatedAtDesc(
                current.getId(), RequestKind.INVITE, RequestStatus.PENDING)) {
            result.add(describe(r, current));
        }
        return result;
    }

    /** Нерешённые заявки и приглашения группы. Доступно владельцу. */
    @Transactional(readOnly = true)
    public List<RequestDto> groupRequests(AppUser current, Long groupId) {
        authMode.requireRequests();
        access.requireOwner(current, groupId);
        List<RequestDto> result = new ArrayList<>();
        for (GroupRequest r : requests.findByGroupIdAndStatusOrderByCreatedAtDesc(
                groupId, RequestStatus.PENDING)) {
            result.add(describe(r, current));
        }
        return result;
    }

    // ------------------------------------------------------------------ решения

    /** Принять приглашение или подтвердить заявку: участник попадает в группу. */
    @Transactional
    public RequestDto accept(AppUser current, Long requestId) {
        authMode.requireRequests();
        GroupRequest request = loadDecidable(current, requestId);
        FriendGroup group = groups.findById(request.getGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));
        AppUser member = users.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));

        if (members.findMembership(group.getId(), member.getId()).isEmpty()) {
            try {
                members.saveAndFlush(new GroupMember(group, member, GroupRole.MEMBER));
            } catch (DataIntegrityViolationException e) {
                // уже добавлен параллельным запросом: это не ошибка
            }
        }
        request.decide(RequestStatus.ACCEPTED, current.getId(), Instant.now());
        requests.save(request);
        return describe(request, current);
    }

    /** Отклонить приглашение или заявку. */
    @Transactional
    public RequestDto decline(AppUser current, Long requestId) {
        authMode.requireRequests();
        GroupRequest request = loadDecidable(current, requestId);
        request.decide(RequestStatus.DECLINED, current.getId(), Instant.now());
        requests.save(request);
        return describe(request, current);
    }

    /** Отозвать то, что создал сам: владелец своё приглашение, участник свою заявку. */
    @Transactional
    public void cancel(AppUser current, Long requestId) {
        authMode.requireRequests();
        GroupRequest request = load(requestId);
        if (!request.getCreatedBy().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Отозвать может только тот, кто создал");
        }
        requirePending(request);
        request.decide(RequestStatus.CANCELLED, current.getId(), Instant.now());
        requests.save(request);
    }

    // ------------------------------------------------------------------ вспомогательное

    private GroupRequest load(Long requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена"));
    }

    private static void requirePending(GroupRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Решение по этой записи уже принято");
        }
    }

    /** Проверяет, что именно этот человек вправе принять решение. */
    private GroupRequest loadDecidable(AppUser current, Long requestId) {
        GroupRequest request = load(requestId);
        requirePending(request);
        if (request.decidedByInvitee()) {
            if (!request.getUserId().equals(current.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Решение по приглашению принимает тот, кого пригласили");
            }
        } else {
            access.requireOwner(current, request.getGroupId());
        }
        return request;
    }

    private RequestDto describe(GroupRequest request, AppUser current) {
        String groupName = groups.findById(request.getGroupId())
                .map(FriendGroup::getName).orElse("?");
        String username = users.findById(request.getUserId())
                .map(AppUser::getUsername).orElse("?");
        String createdBy = users.findById(request.getCreatedBy())
                .map(AppUser::getUsername).orElse("?");
        return toDto(request, groupName, username, createdBy, current);
    }

    private RequestDto toDto(GroupRequest request, String groupName, String username,
                             String createdByName, AppUser current) {
        boolean pending = request.getStatus() == RequestStatus.PENDING;
        boolean mineToDecide = pending && (request.decidedByInvitee()
                ? request.getUserId().equals(current.getId())
                : members.findMembership(request.getGroupId(), current.getId())
                        .map(m -> m.getRole() == GroupRole.OWNER).orElse(false));
        boolean canCancel = pending && request.getCreatedBy().equals(current.getId());

        String message = switch (request.getStatus()) {
            case PENDING -> request.decidedByInvitee()
                    ? (mineToDecide
                        ? "Вас приглашают в группу «" + groupName + "»"
                        : "Приглашение отправлено, ждём ответа от " + username)
                    : (mineToDecide
                        ? username + " просится в группу «" + groupName + "»"
                        : "Заявка подана, ждём решения владельца группы «" + groupName + "»");
            case ACCEPTED -> username + " теперь в группе «" + groupName + "»";
            case DECLINED -> "Отклонено";
            case CANCELLED -> "Отозвано";
        };

        return new RequestDto(request.getId(), request.getGroupId(), groupName, request.getUserId(),
                username, request.getKind().name(), createdByName, request.getCreatedAt(),
                mineToDecide, canCancel, message);
    }
}
