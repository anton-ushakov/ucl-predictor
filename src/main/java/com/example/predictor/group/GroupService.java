package com.example.predictor.group;

import com.example.predictor.auth.AuthService;
import com.example.predictor.common.AuthModeConfig;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.PasswordReset;
import com.example.predictor.repository.PasswordResetRepository;
import java.time.Instant;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.domain.RevealMode;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.domain.SubmissionMode;
import com.example.predictor.scoring.Scoring;
import com.example.predictor.domain.Tiebreaker;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class GroupService {

    public record GroupDto(Long id, String name, String inviteCode, int memberCount, GroupRole myRole) {
    }

    public record MemberDto(Long userId, String username, GroupRole role) {
    }

    /**
     * countExtraTime / countPenalties: прибавлять ли к счёту (который прогнозируют) голы добавочного
     * времени и серии пенальти. Основное время считается всегда.
     */
    public record SettingsDto(int lockMinutesBefore, List<String> tiebreakers, RevealMode revealMode,
                              boolean countExtraTime, boolean countPenalties,
                              SubmissionMode submissionMode,
                              int exactScorePoints, int goalDiffPoints, int outcomePoints,
                              String scoringDescription) {
    }

    /**
     * Временный пароль, выданный участнику владельцем группы. Пароль показывается один раз:
     * передайте его участнику лично, при первом входе приложение попросит задать свой.
     */
    public record ResetPasswordDto(Long userId, String username, String temporaryPassword, String message) {
    }

    public record OptionDto(String code, String description) {
    }

    public record GroupDetailDto(Long id, String name, String inviteCode, GroupRole myRole,
                                 List<MemberDto> members, SettingsDto settings,
                                 List<OptionDto> availableTiebreakers, List<OptionDto> availableRevealModes,
                                 List<OptionDto> availableSubmissionModes, String settingsNote) {
    }

    private static final String SETTINGS_NOTE =
            "Изменения настроек действуют на туры (недели), на которые в группе ещё никто не сохранил прогноз. "
                    + "Для остальных туров правила зафиксированы.";

    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final AppUserRepository users;
    private final GroupAccess access;
    private final AuthModeConfig authMode;
    private final AuthService auth;
    private final PasswordResetRepository passwordResets;

    public GroupService(FriendGroupRepository groups, GroupMemberRepository members,
                        AppUserRepository users, GroupAccess access, AuthModeConfig authMode,
                        AuthService auth, PasswordResetRepository passwordResets) {
        this.authMode = authMode;
        this.auth = auth;
        this.passwordResets = passwordResets;
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.access = access;
    }

    /** В простом режиме кода приглашения нет: вступают по приглашению или заявке. */
    private String visibleInviteCode(FriendGroup group) {
        return authMode.requestsEnabled() ? null : group.getInviteCode();
    }

    @Transactional
    public GroupDto create(AppUser current, String name) {
        FriendGroup group = groups.save(new FriendGroup(name.trim(), uniqueInviteCode()));
        members.save(new GroupMember(group, users.getReferenceById(current.getId()), GroupRole.OWNER));
        return new GroupDto(group.getId(), group.getName(), visibleInviteCode(group), 1, GroupRole.OWNER);
    }

    @Transactional
    public GroupDto join(AppUser current, String inviteCode) {
        authMode.requireInviteCodes();
        String code = inviteCode.trim().toUpperCase(Locale.ROOT);
        FriendGroup group = groups.findByInviteCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа с таким кодом не найдена"));
        GroupRole role = members.findMembership(group.getId(), current.getId())
                .map(GroupMember::getRole)
                .orElse(null);
        if (role == null) {
            members.save(new GroupMember(group, users.getReferenceById(current.getId()), GroupRole.MEMBER));
            role = GroupRole.MEMBER;
        }
        return new GroupDto(group.getId(), group.getName(), visibleInviteCode(group),
                (int) members.countInGroup(group.getId()), role);
    }

    @Transactional(readOnly = true)
    public List<GroupDto> myGroups(AppUser current) {
        List<GroupDto> result = new ArrayList<>();
        for (GroupMember m : members.findAllOfUser(current.getId())) {
            FriendGroup g = m.getGroup();
            result.add(new GroupDto(g.getId(), g.getName(), visibleInviteCode(g),
                    (int) members.countInGroup(g.getId()), m.getRole()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GroupDetailDto detail(AppUser current, Long groupId) {
        Membership ms = access.require(current, groupId);
        FriendGroup g = ms.group();
        List<MemberDto> memberDtos = members.findAllWithUser(g.getId()).stream()
                .map(m -> new MemberDto(m.getUser().getId(), m.getUser().getUsername(), m.getRole()))
                .toList();
        SettingsDto settings = new SettingsDto(g.getLockMinutesBefore(),
                g.tiebreakerList().stream().map(Enum::name).toList(), g.getRevealMode(),
                g.scoreBasis().extraTime(), g.scoreBasis().penalties(), g.getSubmissionMode(),
                g.scoring().exactScorePoints(), g.scoring().goalDiffPoints(),
                g.scoring().outcomePoints(), g.scoring().describe());
        List<OptionDto> tiebreakerOptions = Arrays.stream(Tiebreaker.values())
                .map(t -> new OptionDto(t.name(), t.getDescription())).toList();
        List<OptionDto> revealOptions = Arrays.stream(RevealMode.values())
                .map(r -> new OptionDto(r.name(), r.getDescription())).toList();
        List<OptionDto> submissionOptions = Arrays.stream(SubmissionMode.values())
                .map(m -> new OptionDto(m.name(), m.getDescription())).toList();
        return new GroupDetailDto(g.getId(), g.getName(), visibleInviteCode(g), ms.member().getRole(),
                memberDtos, settings, tiebreakerOptions, revealOptions, submissionOptions, SETTINGS_NOTE);
    }

    /** Только владелец. Прогнозы это не затрагивает: их не может изменить никто. */
    @Transactional
    public SettingsDto updateSettings(AppUser current, Long groupId, int lockMinutesBefore,
                                      List<String> tiebreakerNames, RevealMode revealMode,
                                      ScoreBasis scoreBasis, SubmissionMode submissionMode,
                                      Scoring scoring) {
        Membership ms = access.requireOwner(current, groupId);
        FriendGroup group = ms.group();

        if (lockMinutesBefore < 0 || lockMinutesBefore > 1440) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Закрытие приёма: от 0 до 1440 минут до начала матча");
        }
        List<Tiebreaker> tiebreakers = new ArrayList<>();
        for (String name : tiebreakerNames) {
            Tiebreaker t;
            try {
                t = Tiebreaker.valueOf(name.trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный показатель: " + name);
            }
            if (tiebreakers.contains(t)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Показатель указан дважды: " + name);
            }
            tiebreakers.add(t);
        }

        group.changeSettings(lockMinutesBefore, tiebreakers, revealMode, scoreBasis, submissionMode,
                scoring);
        groups.save(group);
        return new SettingsDto(lockMinutesBefore, tiebreakers.stream().map(Enum::name).toList(), revealMode,
                scoreBasis.extraTime(), scoreBasis.penalties(), submissionMode,
                scoring.exactScorePoints(), scoring.goalDiffPoints(), scoring.outcomePoints(),
                scoring.describe());
    }

    /**
     * Владелец группы сбрасывает пароль участнику, который его забыл и не может войти.
     * Чужие прогнозы это не открывает и не меняет: изменить сохранённый прогноз не может никто.
     * Но временный пароль даёт вход в учётную запись участника, поэтому передавайте его лично,
     * а участник пусть сразу задаст свой пароль.
     */
    @Transactional
    public ResetPasswordDto resetMemberPassword(AppUser current, Long groupId, Long userId) {
        access.requireOwner(current, groupId);
        if (userId.equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Свой пароль меняйте в настройках: для этого нужен текущий пароль или код восстановления");
        }
        GroupMember member = members.findMembership(groupId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "В этой группе нет такого участника"));
        String temporary = auth.resetPassword(userId);
        passwordResets.save(new PasswordReset(userId, groupId, current.getId(), Instant.now()));
        return new ResetPasswordDto(userId, member.getUser().getUsername(), temporary,
                "Передайте пароль участнику лично. Все его входы отозваны, при первом входе "
                        + "приложение попросит задать свой пароль.");
    }

    @Transactional
    public GroupDto regenerateInviteCode(AppUser current, Long groupId) {
        authMode.requireInviteCodes();
        Membership ms = access.requireOwner(current, groupId);
        FriendGroup group = ms.group();
        group.changeInviteCode(uniqueInviteCode());
        groups.save(group);
        return new GroupDto(group.getId(), group.getName(), visibleInviteCode(group),
                (int) members.countInGroup(group.getId()), GroupRole.OWNER);
    }

    private String uniqueInviteCode() {
        String code;
        do {
            code = InviteCodes.generate();
        } while (groups.existsByInviteCode(code));
        return code;
    }
}
