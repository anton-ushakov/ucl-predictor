package com.example.predictor.poll;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** Голосования группы. */
@RestController
@RequestMapping("/api/groups/{groupId}/polls")
public class PollController {

    /** days: через сколько дней закрыть само. Пусто или 0 — пока не закроют руками. */
    public record NewPollRequest(@NotBlank(message = "Напишите вопрос") @Size(max = 200) String question,
                                 boolean openVotes,
                                 Integer days) {
    }

    public record VoteRequest(@NotNull(message = "Ответ не выбран") Boolean yes) {
    }

    private final AuthService auth;
    private final PollService polls;

    public PollController(AuthService auth, PollService polls) {
        this.auth = auth;
        this.polls = polls;
    }

    @GetMapping
    public PollService.PollsView list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return polls.list(user, groupId);
    }

    @PostMapping
    public PollService.PollDto create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody NewPollRequest request) {
        AppUser user = auth.authenticate(authorization);
        return polls.create(user, groupId, request.question(), request.openVotes(), request.days());
    }

    @PostMapping("/{pollId}/vote")
    public PollService.PollDto vote(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("pollId") Long pollId,
            @Valid @RequestBody VoteRequest request) {
        AppUser user = auth.authenticate(authorization);
        return polls.vote(user, groupId, pollId, request.yes());
    }

    @PostMapping("/{pollId}/close")
    public PollService.PollDto close(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("pollId") Long pollId) {
        AppUser user = auth.authenticate(authorization);
        return polls.close(user, groupId, pollId);
    }

    @DeleteMapping("/{pollId}")
    public void delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("pollId") Long pollId) {
        AppUser user = auth.authenticate(authorization);
        polls.delete(user, groupId, pollId);
    }
}
