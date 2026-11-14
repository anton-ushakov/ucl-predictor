package com.example.predictor.notify;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.notify.AnnouncementService.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** Объявления всем участникам. Доступно только владельцу приложения. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /** version — номер новой версии, link — откуда её скачать, note — что изменилось. */
    public record UpdateRequest(@NotBlank(message = "Укажите номер версии") @Size(max = 20) String version,
                                @NotBlank(message = "Укажите ссылку на скачивание") @Size(max = 400) String link,
                                @Size(max = 400) String note) {
    }

    private final AuthService auth;
    private final AnnouncementService announcements;

    public AdminController(AuthService auth, AnnouncementService announcements) {
        this.auth = auth;
        this.announcements = announcements;
    }

    @PostMapping("/announcements")
    public Result announce(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @Valid @RequestBody UpdateRequest request) {
        AppUser user = auth.authenticate(authorization);
        return announcements.announceUpdate(user, request.version(), request.note(), request.link());
    }
}
