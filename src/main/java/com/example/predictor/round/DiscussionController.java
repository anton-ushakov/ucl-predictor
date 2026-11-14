package com.example.predictor.round;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.round.DiscussionService.FeedDto;
import com.example.predictor.round.DiscussionService.MessageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Обсуждение тура: лента сообщений рядом с таблицей. */
@RestController
@RequestMapping("/api/groups/{groupId}/rounds/{week}/messages")
public class DiscussionController {

    public record WriteRequest(@NotBlank(message = "Сообщение пустое")
                               @Size(max = 1000) String text) {
    }

    private final AuthService auth;
    private final DiscussionService discussion;

    public DiscussionController(AuthService auth, DiscussionService discussion) {
        this.auth = auth;
        this.discussion = discussion;
    }

    /** afterId: прислать только то, что появилось после уже показанного сообщения. */
    @GetMapping
    public FeedDto feed(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable("groupId") Long groupId,
                        @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                        @RequestParam(value = "afterId", required = false) Long afterId) {
        AppUser user = auth.authenticate(authorization);
        return discussion.feed(user, groupId, week, afterId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto write(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @PathVariable("groupId") Long groupId,
                            @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                            @Valid @RequestBody WriteRequest request) {
        AppUser user = auth.authenticate(authorization);
        return discussion.write(user, groupId, week, request.text());
    }

    @DeleteMapping("/{messageId}")
    public MessageDto delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @PathVariable("groupId") Long groupId,
                             @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             @PathVariable("messageId") Long messageId) {
        AppUser user = auth.authenticate(authorization);
        return discussion.delete(user, groupId, week, messageId);
    }
}
