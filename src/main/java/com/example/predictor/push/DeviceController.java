package com.example.predictor.push;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.DeviceToken;
import com.example.predictor.repository.DeviceTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Адрес телефона для мгновенных оповещений. Приложение присылает его при входе
 * и убирает при выходе, чтобы оповещения не приходили тому, кто уже вышел.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    public record DeviceRequest(@NotBlank @Size(max = 500) String token) {
    }

    public record DeviceResult(boolean push, String message) {
    }

    private final AuthService auth;
    private final DeviceTokenRepository devices;
    private final FirebaseAccess firebase;

    public DeviceController(AuthService auth, DeviceTokenRepository devices,
                            FirebaseAccess firebase) {
        this.auth = auth;
        this.devices = devices;
        this.firebase = firebase;
    }

    @PostMapping
    @Transactional
    public DeviceResult register(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DeviceRequest request) {
        AppUser user = auth.authenticate(authorization);
        String token = request.token().trim();
        Instant now = Instant.now();
        devices.findByToken(token).ifPresentOrElse(
                existing -> {
                    existing.touch(user.getId(), now);
                    devices.save(existing);
                },
                () -> devices.save(new DeviceToken(user.getId(), token, now)));
        return new DeviceResult(firebase.isConfigured(),
                firebase.isConfigured()
                        ? "Телефон будет получать оповещения сразу"
                        : "Мгновенные оповещения на сервере не настроены");
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unregister(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DeviceRequest request) {
        auth.authenticate(authorization);
        devices.deleteByToken(request.token().trim());
    }
}
