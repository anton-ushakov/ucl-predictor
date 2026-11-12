package com.example.predictor.notify;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.notify.NotificationService.InboxDto;
import com.example.predictor.notify.NotificationService.SettingsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record ReadRequest(List<Long> ids) {
    }

    public record UpdateSettingsRequest(@NotNull Boolean lockSoon,
                                        @NotNull Boolean matchFinished,
                                        @NotNull Boolean summary) {
    }

    private final AuthService auth;
    private final NotificationService notifications;

    public NotificationController(AuthService auth, NotificationService notifications) {
        this.auth = auth;
        this.notifications = notifications;
    }

    /** Оповещения участника, новые сверху, вместе с его настройками. */
    @GetMapping
    public InboxDto inbox(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        AppUser user = auth.authenticate(authorization);
        return notifications.inbox(user, unreadOnly);
    }

    /** Отметить прочитанными: указанные или все, если список пуст. */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody(required = false) ReadRequest request) {
        AppUser user = auth.authenticate(authorization);
        notifications.markRead(user, request == null ? null : request.ids());
    }

    @GetMapping("/settings")
    public SettingsDto settings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.authenticate(authorization);
        return notifications.settings(user);
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateSettingsRequest request) {
        AppUser user = auth.authenticate(authorization);
        return notifications.updateSettings(user, request.lockSoon(), request.matchFinished(),
                request.summary());
    }
}
