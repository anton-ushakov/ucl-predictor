package com.example.predictor.auth;

/**
 * Ответ на регистрацию, вход и смену пароля. Токен показывается один раз, телефон его сохраняет.
 * recoveryCode заполняется только когда выдан новый код восстановления (регистрация, восстановление
 * доступа, запрос нового кода): показать его пользователю и предложить сохранить.
 * mustChangePassword: вход по временному паролю от владельца группы, нужно сразу задать свой.
 */
public record AuthResponse(String token, Long userId, String username,
                           String recoveryCode, boolean mustChangePassword) {
}
