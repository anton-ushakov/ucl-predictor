package com.example.predictor.auth;

import com.example.predictor.common.AuthModeConfig;
import com.example.predictor.domain.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 30)
            @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "только латинские буквы, цифры и символы _ . -")
            String username,

            @NotBlank
            @Size(min = 6, max = 72)
            String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record MeResponse(Long id, String username, boolean mustChangePassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 6, max = 72) String newPassword) {
    }

    public record RecoverRequest(@NotBlank String username,
                                 @NotBlank String recoveryCode,
                                 @NotBlank @Size(min = 6, max = 72) String newPassword) {
    }

    public record NewRecoveryCodeRequest(@NotBlank String password) {
    }

    /** Регистрация и вход в простом режиме: нужен только логин. */
    public record SimpleAuthRequest(
            @NotBlank @Size(min = 3, max = 30)
            @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Имя: латиница, цифры и знаки _ . -")
            String username) {
    }

    /** Что умеет этот сервер: приложение подстраивает экраны под режим входа. */
    public record ServerConfigDto(String authMode, boolean passwordsEnabled, boolean requestsEnabled,
                                  String description) {
    }

    private final AuthService auth;
    private final AuthModeConfig authMode;

    public AuthController(AuthService auth, AuthModeConfig authMode) {
        this.authMode = authMode;
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.username(), request.password());
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
    }

    /** Открыто без входа: приложению нужно знать режим до того, как показать экран входа. */
    @GetMapping("/config")
    public ServerConfigDto config() {
        return new ServerConfigDto(authMode.mode().name(), authMode.passwordsEnabled(),
                authMode.requestsEnabled(),
                authMode.passwordsEnabled()
                        ? "Вход по логину и паролю, вступление в группу по коду приглашения"
                        : "Вход по логину, вступление в группу по приглашению или заявке");
    }

    /** Простой режим: первый вход, придумываем имя. */
    @PostMapping("/auth/simple/register")
    public AuthResponse registerSimple(@Valid @RequestBody SimpleAuthRequest request) {
        return auth.registerSimple(request.username());
    }

    /** Простой режим: вход на новом телефоне по тому же имени. */
    @PostMapping("/auth/simple/login")
    public AuthResponse loginSimple(@Valid @RequestBody SimpleAuthRequest request) {
        return auth.loginSimple(request.username());
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.authenticate(authorization);
        return new MeResponse(user.getId(), user.getUsername(), user.isMustChangePassword());
    }

    /** Смена пароля тем, кто помнит текущий. В ответе новый токен: старые входы отзываются. */
    @PostMapping("/auth/password")
    public AuthResponse changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        AppUser user = auth.authenticate(authorization);
        return auth.changePassword(user, request.currentPassword(), request.newPassword());
    }

    /** Новый пароль по коду восстановления: вход не нужен. В ответе токен и новый код восстановления. */
    @PostMapping("/auth/recover")
    public AuthResponse recover(@Valid @RequestBody RecoverRequest request) {
        return auth.recover(request.username(), request.recoveryCode(), request.newPassword());
    }

    /** Новый код восстановления вместо потерянного. Нужен текущий пароль. */
    @PostMapping("/auth/recovery-code")
    public AuthResponse newRecoveryCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody NewRecoveryCodeRequest request) {
        AppUser user = auth.authenticate(authorization);
        return auth.newRecoveryCode(user, request.password());
    }
}
