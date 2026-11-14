package com.example.predictor.notify;

import com.example.predictor.common.AdminAccess;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Notification;
import com.example.predictor.domain.NotificationType;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Объявление о новой версии приложения: владелец сервера рассылает его всем сразу.
 * Ключ объявления — номер версии, поэтому повторная отправка той же версии никого не беспокоит.
 */
@Service
public class AnnouncementService {

    public record Result(int sent, int skipped, String message) {
    }

    private final AdminAccess admin;
    private final AppUserRepository users;
    private final NotificationRepository notifications;
    private final com.example.predictor.push.PushSender push;

    public AnnouncementService(AdminAccess admin, AppUserRepository users,
                               NotificationRepository notifications,
                               com.example.predictor.push.PushSender push) {
        this.admin = admin;
        this.users = users;
        this.notifications = notifications;
        this.push = push;
    }

    @Transactional
    public Result announceUpdate(AppUser sender, String version, String note, String link) {
        admin.require(sender);
        String cleanVersion = version == null ? "" : version.trim();
        if (cleanVersion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите номер версии");
        }
        String cleanLink = link == null ? "" : link.trim();
        if (cleanLink.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите ссылку, откуда скачать новую версию");
        }
        if (!cleanLink.startsWith("https://") && !cleanLink.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылка должна начинаться с https://");
        }

        String title = "Вышла версия " + cleanVersion;
        String body = buildBody(note, cleanLink);
        String dedupKey = "app-update:" + cleanVersion;

        List<AppUser> everyone = users.findAll();
        List<Long> ids = new ArrayList<>(everyone.size());
        for (AppUser u : everyone) {
            ids.add(u.getId());
        }
        // кому это объявление уже уходило — тем второй раз не шлём
        Set<Long> already = new HashSet<>();
        if (!ids.isEmpty()) {
            for (Notification n : notifications.findExisting(ids, List.of(dedupKey))) {
                already.add(n.getUserId());
            }
        }

        Instant now = Instant.now();
        List<Notification> fresh = new ArrayList<>();
        for (AppUser u : everyone) {
            if (already.contains(u.getId())) {
                continue;
            }
            fresh.add(new Notification(u.getId(), null, NotificationType.APP_UPDATE, dedupKey,
                    title, body, null, now));
        }
        notifications.saveAll(fresh);
        notifications.flush();
        push.push(fresh);

        String message = fresh.isEmpty()
                ? "Об этой версии уже сообщали всем участникам"
                : "Объявление отправлено участникам: " + fresh.size();
        return new Result(fresh.size(), already.size(), message);
    }

    private static String buildBody(String note, String link) {
        StringBuilder text = new StringBuilder();
        if (note != null && !note.isBlank()) {
            text.append(note.trim()).append("\n\n");
        }
        text.append("Скачать: ").append(link);
        String body = text.toString();
        return body.length() > 600 ? body.substring(0, 600) : body;
    }
}
