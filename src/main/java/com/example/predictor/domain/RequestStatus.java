package com.example.predictor.domain;

/** Что стало с приглашением или заявкой. */
public enum RequestStatus {

    /** Ждёт решения. */
    PENDING,

    /** Принято: участник в группе. */
    ACCEPTED,

    /** Отклонено. */
    DECLINED,

    /** Отозвано тем, кто его создал. */
    CANCELLED
}
