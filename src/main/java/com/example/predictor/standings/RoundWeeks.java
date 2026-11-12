package com.example.predictor.standings;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Тур = неделя с понедельника по воскресенье. Тур обозначается датой понедельника. */
public final class RoundWeeks {

    private RoundWeeks() {
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate mondayOf(Instant instant, ZoneId zone) {
        return mondayOf(instant.atZone(zone).toLocalDate());
    }
}
