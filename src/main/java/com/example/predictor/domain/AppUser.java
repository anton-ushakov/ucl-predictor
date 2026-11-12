package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Хеш кода восстановления доступа (сам код хранится только у пользователя). */
    @Column(name = "recovery_hash", length = 100)
    private String recoveryHash;

    @Column(name = "recovery_set_at")
    private Instant recoverySetAt;

    /** Пароль выдан владельцем группы: до первой смены приложение требует задать свой. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** Защита кода восстановления от перебора. */
    @Column(name = "recovery_fails", nullable = false)
    private int recoveryFails;

    @Column(name = "recovery_blocked_until")
    private Instant recoveryBlockedUntil;

    // ---- PIN-код простого режима ----

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(name = "pin_set_at")
    private Instant pinSetAt;

    /** PIN нужно задать при следующем входе: его ещё нет или владелец группы его сбросил. */
    @Column(name = "must_set_pin", nullable = false)
    private boolean mustSetPin;

    @Column(name = "pin_fails", nullable = false)
    private int pinFails;

    @Column(name = "pin_blocked_until")
    private Instant pinBlockedUntil;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /** Пользователь сам задал новый пароль. */
    public void changePassword(String passwordHash, Instant at) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = at;
        this.mustChangePassword = false;
    }

    /** Пароль выдан владельцем группы: его нужно сменить при первом входе. */
    public void setTemporaryPassword(String passwordHash, Instant at) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = at;
        this.mustChangePassword = true;
    }

    public void setRecovery(String recoveryHash, Instant at) {
        this.recoveryHash = recoveryHash;
        this.recoverySetAt = at;
        this.recoveryFails = 0;
        this.recoveryBlockedUntil = null;
    }

    /** Неверный код восстановления: после нескольких попыток подбор временно блокируется. */
    public void recoveryFailed(int maxFails, Instant blockedUntil) {
        this.recoveryFails++;
        if (this.recoveryFails >= maxFails) {
            this.recoveryFails = 0;
            this.recoveryBlockedUntil = blockedUntil;
        }
    }

    public boolean isRecoveryBlocked(Instant now) {
        return recoveryBlockedUntil != null && recoveryBlockedUntil.isAfter(now);
    }

    public void setPin(String pinHash, Instant at) {
        this.pinHash = pinHash;
        this.pinSetAt = at;
        this.mustSetPin = false;
        this.pinFails = 0;
        this.pinBlockedUntil = null;
    }

    /** Владелец группы сбросил PIN: участник задаст новый при следующем входе. */
    public void clearPin() {
        this.pinHash = null;
        this.pinSetAt = null;
        this.mustSetPin = true;
        this.pinFails = 0;
        this.pinBlockedUntil = null;
    }

    public void pinFailed(int maxFails, Instant blockedUntil) {
        this.pinFails++;
        if (this.pinFails >= maxFails) {
            this.pinFails = 0;
            this.pinBlockedUntil = blockedUntil;
        }
    }

    public void pinAccepted() {
        this.pinFails = 0;
        this.pinBlockedUntil = null;
    }

    public boolean isPinBlocked(Instant now) {
        return pinBlockedUntil != null && pinBlockedUntil.isAfter(now);
    }

    public boolean hasPin() {
        return pinHash != null;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRecoveryHash() { return recoveryHash; }
    public Instant getRecoverySetAt() { return recoverySetAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public Instant getRecoveryBlockedUntil() { return recoveryBlockedUntil; }
    public String getPinHash() { return pinHash; }
    public Instant getPinSetAt() { return pinSetAt; }
    public boolean isMustSetPin() { return mustSetPin; }
    public Instant getPinBlockedUntil() { return pinBlockedUntil; }
}
