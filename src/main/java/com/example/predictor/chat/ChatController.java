package com.example.predictor.chat;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** Чат группы. */
@RestController
@RequestMapping("/api/groups/{groupId}/chat")
public class ChatController {

    public record WriteRequest(@NotBlank(message = "Сообщение пустое") @Size(max = 1000) String text) {
    }

    /** lastId — до какого сообщения дочитали. */
    public record ReadRequest(Long lastId) {
    }

    public record UnreadDto(long unread) {
    }

    private final AuthService auth;
    private final ChatService chat;

    public ChatController(AuthService auth, ChatService chat) {
        this.auth = auth;
        this.chat = chat;
    }

    @GetMapping
    public ChatService.FeedDto feed(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @RequestParam(value = "afterId", required = false) Long afterId) {
        AppUser user = auth.authenticate(authorization);
        return chat.feed(user, groupId, afterId);
    }

    @PostMapping
    public ChatService.MessageDto write(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody WriteRequest request) {
        AppUser user = auth.authenticate(authorization);
        return chat.write(user, groupId, request.text());
    }

    @DeleteMapping("/{messageId}")
    public ChatService.MessageDto delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("messageId") Long messageId) {
        AppUser user = auth.authenticate(authorization);
        return chat.delete(user, groupId, messageId);
    }

    @PostMapping("/read")
    public UnreadDto read(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @RequestBody(required = false) ReadRequest request) {
        AppUser user = auth.authenticate(authorization);
        return new UnreadDto(chat.markRead(user, groupId,
                request == null ? null : request.lastId()));
    }
}
