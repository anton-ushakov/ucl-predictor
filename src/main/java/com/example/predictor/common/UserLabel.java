package com.example.predictor.common;

import com.example.predictor.domain.AppUser;

/**
 * Как участник подписан в списках: имя и выбранный им значок.
 * Значок может отсутствовать — тогда показывается только имя.
 */
public record UserLabel(String username, String badge) {

    /** Для участника, которого почему-то не нашли: такого быть не должно, но пусто лучше ошибки. */
    public static final UserLabel UNKNOWN = new UserLabel("?", null);

    /** Имя со значком, одной строкой: так участник подписан в простых списках. */
    public String display() {
        return badge == null || badge.isBlank() ? username : badge + " " + username;
    }

    public static UserLabel of(AppUser user) {
        return new UserLabel(user.getUsername(), user.getBadge());
    }
}
