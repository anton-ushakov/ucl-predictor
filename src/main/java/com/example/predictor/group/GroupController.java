package com.example.predictor.group;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.RevealMode;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.domain.SubmissionMode;
import com.example.predictor.scoring.Scoring;
import com.example.predictor.group.GroupService.GroupDetailDto;
import com.example.predictor.group.GroupService.GroupDto;
import com.example.predictor.group.GroupService.ResetPasswordDto;
import com.example.predictor.group.GroupService.SettingsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    public record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record JoinGroupRequest(@NotBlank @Size(max = 16) String inviteCode) {
    }

    public record UpdateSettingsRequest(
            @NotNull @Min(0) @Max(1440) Integer lockMinutesBefore,
            @NotNull @Size(max = 4) List<String> tiebreakers,
            @NotNull RevealMode revealMode,
            Boolean countExtraTime,
            Boolean countPenalties,
            SubmissionMode submissionMode,
            @Min(0) @Max(100) Integer exactScorePoints,
            @Min(0) @Max(100) Integer goalDiffPoints,
            @Min(0) @Max(100) Integer outcomePoints) {
    }

    private final AuthService auth;
    private final GroupService groups;

    public GroupController(AuthService auth, GroupService groups) {
        this.auth = auth;
        this.groups = groups;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto create(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @Valid @RequestBody CreateGroupRequest request) {
        AppUser user = auth.authenticate(authorization);
        return groups.create(user, request.name());
    }

    @PostMapping("/join")
    public GroupDto join(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @Valid @RequestBody JoinGroupRequest request) {
        AppUser user = auth.authenticate(authorization);
        return groups.join(user, request.inviteCode());
    }

    @GetMapping
    public List<GroupDto> mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.authenticate(authorization);
        return groups.myGroups(user);
    }

    @GetMapping("/{groupId}")
    public GroupDetailDto detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return groups.detail(user, groupId);
    }

    @PutMapping("/{groupId}/settings")
    public SettingsDto updateSettings(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable("groupId") Long groupId,
                                      @Valid @RequestBody UpdateSettingsRequest request) {
        AppUser user = auth.authenticate(authorization);
        ScoreBasis scoreBasis = new ScoreBasis(Boolean.TRUE.equals(request.countExtraTime()),
                Boolean.TRUE.equals(request.countPenalties()));
        SubmissionMode submissionMode = request.submissionMode() == null
                ? SubmissionMode.WHOLE_ROUND : request.submissionMode();
        Scoring scoring = new Scoring(
                request.exactScorePoints() == null ? Scoring.DEFAULT.exactScorePoints()
                        : request.exactScorePoints(),
                request.goalDiffPoints() == null ? Scoring.DEFAULT.goalDiffPoints()
                        : request.goalDiffPoints(),
                request.outcomePoints() == null ? Scoring.DEFAULT.outcomePoints()
                        : request.outcomePoints());
        return groups.updateSettings(user, groupId, request.lockMinutesBefore(),
                request.tiebreakers(), request.revealMode(), scoreBasis, submissionMode, scoring);
    }

    /** Сброс пароля участнику (только владелец). Временный пароль показывается один раз. */
    @PostMapping("/{groupId}/members/{userId}/reset-password")
    public ResetPasswordDto resetMemberPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        AppUser user = auth.authenticate(authorization);
        return groups.resetMemberPassword(user, groupId, userId);
    }

    @PostMapping("/{groupId}/invite-code")
    public GroupDto regenerateInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return groups.regenerateInviteCode(user, groupId);
    }
}
