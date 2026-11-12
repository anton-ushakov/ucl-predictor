package com.example.predictor.membership;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.membership.MembershipService.CandidateDto;
import com.example.predictor.membership.MembershipService.OpenGroupDto;
import com.example.predictor.membership.MembershipService.RequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Приглашения в группу и заявки на вступление (простой режим входа). */
@RestController
@RequestMapping("/api")
public class MembershipController {

    public record InviteRequest(@NotBlank String username) {
    }

    public record BulkInviteRequest(@NotEmpty @Size(max = 200) List<Long> userIds) {
    }

    private final AuthService auth;
    private final MembershipService membership;

    public MembershipController(AuthService auth, MembershipService membership) {
        this.auth = auth;
        this.membership = membership;
    }

    /** Группы, в которых я не состою: можно подать заявку. */
    @GetMapping("/groups/open")
    public List<OpenGroupDto> openGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return membership.openGroups(auth.authenticate(authorization));
    }

    /** Подать заявку на вступление. Решение примет владелец группы. */
    @PostMapping("/groups/{groupId}/join-request")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto requestToJoin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId) {
        return membership.requestToJoin(auth.authenticate(authorization), groupId);
    }

    /** Пригласить участника по имени. Доступно владельцу группы. */
    @PostMapping("/groups/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto invite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody InviteRequest request) {
        return membership.invite(auth.authenticate(authorization), groupId, request.username());
    }

    /** Все участники приложения с пометками: для экрана выбора галочками. */
    @GetMapping("/groups/{groupId}/candidates")
    public List<CandidateDto> candidates(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId) {
        return membership.candidates(auth.authenticate(authorization), groupId);
    }

    /** Пригласить сразу нескольких отмеченных участников. */
    @PostMapping("/groups/{groupId}/invites/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<RequestDto> inviteMany(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody BulkInviteRequest request) {
        return membership.inviteMany(auth.authenticate(authorization), groupId, request.userIds());
    }

    /** Исключить участника из группы. История его прогнозов сохраняется. */
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        membership.removeMember(auth.authenticate(authorization), groupId, userId);
    }

    /** Сбросить участнику PIN-код: он задаст новый при следующем входе. */
    @PostMapping("/groups/{groupId}/members/{userId}/reset-pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetMemberPin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        membership.resetMemberPin(auth.authenticate(authorization), groupId, userId);
    }

    /** Нерешённые заявки и приглашения группы. Доступно владельцу. */
    @GetMapping("/groups/{groupId}/requests")
    public List<RequestDto> groupRequests(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId) {
        return membership.groupRequests(auth.authenticate(authorization), groupId);
    }

    /** Приглашения, адресованные мне. */
    @GetMapping("/invitations")
    public List<RequestDto> myInvitations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return membership.myInvitations(auth.authenticate(authorization));
    }

    @PostMapping("/requests/{requestId}/accept")
    public RequestDto accept(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("requestId") Long requestId) {
        return membership.accept(auth.authenticate(authorization), requestId);
    }

    @PostMapping("/requests/{requestId}/decline")
    public RequestDto decline(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("requestId") Long requestId) {
        return membership.decline(auth.authenticate(authorization), requestId);
    }

    /** Отозвать своё приглашение или свою заявку. */
    @DeleteMapping("/requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("requestId") Long requestId) {
        membership.cancel(auth.authenticate(authorization), requestId);
    }
}
