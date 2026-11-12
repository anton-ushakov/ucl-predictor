package com.example.predictor.auth;

import com.example.predictor.common.AuthModeConfig;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.AuthToken;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.AuthTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    /** Долгоживущий токен: телефон хранит его и не просит пароль каждый раз. */
    static final Duration TOKEN_LIFETIME = Duration.ofDays(180);

    private static final String BEARER_PREFIX = "Bearer ";

    /** Столько неверных кодов восстановления подряд, и подбор блокируется на RECOVERY_BLOCK. */
    static final int MAX_RECOVERY_FAILS = 5;
    static final Duration RECOVERY_BLOCK = Duration.ofMinutes(15);

    /** На столько блокируется вход после череды неверных PIN-кодов. */
    static final Duration PIN_BLOCK = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final AuthModeConfig authMode;

    public AuthService(AppUserRepository users, AuthTokenRepository tokens, PasswordEncoder encoder,
                       AuthModeConfig authMode) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.authMode = authMode;
    }

    // ------------------------------------------------------------------ простой режим

    /**
     * Регистрация по одному логину: пароля нет, телефон запоминает выданный токен.
     * В базе всё равно лежит хеш, только от случайной строки, которую никто не знает:
     * так вход по паролю для такой учётной записи невозможен, а переключить режим можно
     * в любой момент (достаточно сбросить пароль через владельца группы).
     */
    /**
     * Приводит имя к виду, в котором оно хранится: убирает пробелы по краям.
     * Регистр сохраняется, но уникальность проверяется без его учёта (индекс по lower(username)).
     */
    static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    @Transactional
    public AuthResponse registerSimple(String username, String pin) {
        String name = normalizeUsername(username);
        checkPin(pin);
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это имя уже занято");
        }
        try {
            AppUser user = users.save(new AppUser(name, encoder.encode(unusablePassword())));
            user.setPin(encoder.encode(Pins.normalize(pin)), Instant.now());
            users.save(user);
            return issueToken(user, null);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это имя уже занято");
        }
    }

    /**
     * Вход по логину и PIN-коду. Пароля в простом режиме нет: PIN нужен, чтобы посторонний
     * не вошёл под чужим именем, зная только логин.
     *
     * <p>У кого PIN ещё не задан (например, зарегистрировался до появления PIN или владелец
     * группы сбросил его), входит без PIN, но получает mustSetPin и обязан сразу задать свой.
     */
    @Transactional
    public AuthResponse loginSimple(String username, String pin) {
        AppUser user = users.findByUsernameIgnoreCase(normalizeUsername(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Нет участника с таким именем"));
        Instant now = Instant.now();

        if (!user.hasPin()) {
            return issueToken(user, null);
        }
        if (user.isPinBlocked(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Слишком много неверных попыток. Попробуйте через 15 минут или попросите "
                            + "владельца группы сбросить PIN-код.");
        }
        String value = Pins.normalize(pin);
        if (value.isEmpty() || !encoder.matches(value, user.getPinHash())) {
            user.pinFailed(Pins.MAX_FAILS, now.plus(PIN_BLOCK));
            users.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный PIN-код");
        }
        user.pinAccepted();
        users.save(user);
        return issueToken(user, null);
    }

    /** Задать или сменить PIN-код. Доступно тому, кто уже вошёл. */
    @Transactional
    public AuthResponse setPin(AppUser current, String pin) {
        AppUser user = users.findById(current.getId()).orElseThrow(AuthService::unauthorized);
        checkPin(pin);
        user.setPin(encoder.encode(Pins.normalize(pin)), Instant.now());
        users.save(user);
        return new AuthResponse(null, user.getId(), user.getUsername(), null,
                user.isMustChangePassword(), user.isMustSetPin());
    }

    /**
     * Сброс PIN-кода участнику: он задаст новый при следующем входе.
     * Все его входы отзываются. Проверку прав делает вызывающий код (владелец группы).
     */
    @Transactional
    public void resetPin(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));
        user.clearPin();
        users.save(user);
        tokens.deleteAllOfUser(user.getId());
    }

    private static void checkPin(String pin) {
        String error = Pins.validate(pin);
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }
    }

    private static String unusablePassword() {
        return "no-password:" + TokenUtil.newToken();
    }

    // ------------------------------------------------------------------ парольный режим

    @Transactional
    public AuthResponse register(String username, String password) {
        authMode.requirePasswords();
        String name = normalizeUsername(username);
        // BCrypt учитывает только первые 72 байта пароля
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль слишком длинный");
        }
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это имя уже занято");
        }
        AppUser user;
        try {
            user = users.save(new AppUser(name, encoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // два запроса с одним именем одновременно
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это имя уже занято");
        }
        String recoveryCode = RecoveryCodes.generate();
        user.setRecovery(encoder.encode(RecoveryCodes.normalize(recoveryCode)), Instant.now());
        users.save(user);
        return issueToken(user, recoveryCode);
    }

    @Transactional
    public AuthResponse login(String username, String password) {
        authMode.requirePasswords();
        AppUser user = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(AuthService::badCredentials);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw badCredentials();
        }
        return issueToken(user, null);
    }

    /** Смена пароля тем, кто помнит старый. Остальные входы отзываются. */
    @Transactional
    public AuthResponse changePassword(AppUser current, String currentPassword, String newPassword) {
        authMode.requirePasswords();
        AppUser user = users.findById(current.getId()).orElseThrow(AuthService::unauthorized);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Текущий пароль указан неверно");
        }
        checkNewPassword(newPassword);
        user.changePassword(encoder.encode(newPassword), Instant.now());
        users.save(user);
        tokens.deleteAllOfUser(user.getId());
        return issueToken(user, null);
    }

    /**
     * Новый пароль по коду восстановления: для того, кто забыл пароль или сменил телефон.
     * Старый код после этого не действует, выдаётся новый.
     */
    @Transactional
    public AuthResponse recover(String username, String recoveryCode, String newPassword) {
        authMode.requirePasswords();
        AppUser user = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        Instant now = Instant.now();
        if (user == null || user.getRecoveryHash() == null) {
            throw badRecoveryCode();
        }
        if (user.isRecoveryBlocked(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Слишком много попыток. Попробуйте через 15 минут или попросите владельца группы "
                            + "сбросить пароль.");
        }
        String normalized = RecoveryCodes.normalize(recoveryCode);
        if (normalized.isEmpty() || !encoder.matches(normalized, user.getRecoveryHash())) {
            user.recoveryFailed(MAX_RECOVERY_FAILS, now.plus(RECOVERY_BLOCK));
            users.save(user);
            throw badRecoveryCode();
        }
        checkNewPassword(newPassword);
        user.changePassword(encoder.encode(newPassword), now);
        String freshCode = RecoveryCodes.generate();
        user.setRecovery(encoder.encode(RecoveryCodes.normalize(freshCode)), now);
        users.save(user);
        tokens.deleteAllOfUser(user.getId());
        return issueToken(user, freshCode);
    }

    /** Новый код восстановления вместо потерянного. Нужен текущий пароль. */
    @Transactional
    public AuthResponse newRecoveryCode(AppUser current, String password) {
        authMode.requirePasswords();
        AppUser user = users.findById(current.getId()).orElseThrow(AuthService::unauthorized);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пароль указан неверно");
        }
        String code = RecoveryCodes.generate();
        user.setRecovery(encoder.encode(RecoveryCodes.normalize(code)), Instant.now());
        users.save(user);
        return new AuthResponse(null, user.getId(), user.getUsername(), code,
                user.isMustChangePassword(), user.isMustSetPin());
    }

    /**
     * Сброс пароля участнику: временный пароль, который владелец группы передаёт ему лично.
     * Все входы участника отзываются, при первом входе приложение потребует задать свой пароль.
     * Проверку прав делает вызывающий код (владелец группы).
     */
    @Transactional
    public String resetPassword(Long userId) {
        authMode.requirePasswords();
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));
        String temporary = TempPasswords.generate();
        user.setTemporaryPassword(encoder.encode(temporary), Instant.now());
        users.save(user);
        tokens.deleteAllOfUser(user.getId());
        return temporary;
    }

    private void checkNewPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль не короче 6 символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль слишком длинный");
        }
    }

    /** Находит пользователя по заголовку Authorization: Bearer ... или отвечает 401. */
    @Transactional(readOnly = true)
    public AppUser authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw unauthorized();
        }
        String rawToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        AuthToken token = tokens.findByTokenHashWithUser(TokenUtil.sha256Hex(rawToken))
                .orElseThrow(AuthService::unauthorized);
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw unauthorized();
        }
        return token.getUser();
    }

    @Transactional
    public void logout(String authorizationHeader) {
        authenticate(authorizationHeader);
        String rawToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        tokens.deleteByTokenHash(TokenUtil.sha256Hex(rawToken));
    }

    private AuthResponse issueToken(AppUser user, String recoveryCode) {
        String rawToken = TokenUtil.newToken();
        Instant now = Instant.now();
        tokens.save(new AuthToken(user, TokenUtil.sha256Hex(rawToken), now, now.plus(TOKEN_LIFETIME)));
        return new AuthResponse(rawToken, user.getId(), user.getUsername(),
                recoveryCode, user.isMustChangePassword(), user.isMustSetPin());
    }

    private static ResponseStatusException badRecoveryCode() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверное имя или код восстановления");
    }

    private static ResponseStatusException badCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверное имя или пароль");
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется вход");
    }
}
