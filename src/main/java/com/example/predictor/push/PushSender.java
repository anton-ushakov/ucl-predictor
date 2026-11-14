package com.example.predictor.push;

import com.example.predictor.domain.DeviceToken;
import com.example.predictor.domain.Notification;
import com.example.predictor.repository.DeviceTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Отправка оповещений на телефоны через Firebase.
 *
 * Отправка — дело необязательное: если Firebase не настроен или не ответил, приложение
 * всё равно заберёт оповещения опросом. Поэтому ошибки здесь только записываются в лог
 * и никогда не отменяют сохранение самого оповещения.
 */
@Service
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final FirebaseAccess firebase;
    private final DeviceTokenRepository devices;
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient http = RestClient.create();

    public PushSender(FirebaseAccess firebase, DeviceTokenRepository devices) {
        this.firebase = firebase;
        this.devices = devices;
    }

    /** Рассылает пачку только что созданных оповещений их получателям. */
    public void push(List<Notification> notifications) {
        if (!firebase.isConfigured() || notifications == null || notifications.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (Notification n : notifications) {
            userIds.add(n.getUserId());
        }
        Map<Long, List<DeviceToken>> byUser = new HashMap<>();
        for (DeviceToken token : devices.findByUserIdIn(userIds)) {
            byUser.computeIfAbsent(token.getUserId(), k -> new ArrayList<>()).add(token);
        }
        if (byUser.isEmpty()) {
            return;
        }

        String accessToken;
        try {
            accessToken = firebase.accessToken();
        } catch (RuntimeException e) {
            log.warn("Firebase: не удалось получить токен доступа: {}", e.getMessage());
            return;
        }

        int sent = 0;
        List<String> dead = new ArrayList<>();
        for (Notification n : notifications) {
            for (DeviceToken device : byUser.getOrDefault(n.getUserId(), List.of())) {
                if (send(accessToken, device.getToken(), n, dead)) {
                    sent++;
                }
            }
        }
        for (String token : dead) {
            devices.deleteByToken(token);
        }
        if (sent > 0 || !dead.isEmpty()) {
            log.info("Отправлено на телефоны: {}; забытых адресов убрано: {}", sent, dead.size());
        }
    }

    private boolean send(String accessToken, String deviceToken, Notification n, List<String> dead) {
        ObjectNode message = json.createObjectNode();
        ObjectNode body = message.putObject("message");
        body.put("token", deviceToken);
        ObjectNode notification = body.putObject("notification");
        notification.put("title", n.getTitle());
        notification.put("body", n.getBody());
        // данные нужны приложению, чтобы открыть нужный экран
        ObjectNode data = body.putObject("data");
        data.put("type", n.getType().name());
        data.put("notificationId", String.valueOf(n.getId()));
        if (n.getGroupId() != null) {
            data.put("groupId", String.valueOf(n.getGroupId()));
        }
        if (n.getRoundWeek() != null) {
            data.put("week", n.getRoundWeek().toString());
        }
        body.putObject("android").put("priority", "high");

        try {
            http.post()
                    .uri("https://fcm.googleapis.com/v1/projects/" + firebase.projectId()
                            + "/messages:send")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int status = response.getStatusCode().value();
                        // приложение удалили или адрес устарел: такой адрес больше не нужен
                        if (status == 404 || status == 400) {
                            dead.add(deviceToken);
                        }
                        throw new IllegalStateException("Firebase ответил " + status);
                    })
                    .body(String.class);
            return true;
        } catch (RuntimeException e) {
            log.debug("Не удалось отправить оповещение на телефон: {}", e.getMessage());
            return false;
        }
    }
}
