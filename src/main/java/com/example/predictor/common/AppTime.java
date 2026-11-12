package com.example.predictor.common;

import com.example.predictor.standings.RoundWeeks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Часовой пояс приложения и границы туров (недель). */
@Component
public class AppTime {

    private final ZoneId zone;
    private final int weeksAhead;

    public AppTime(@Value("${app.timezone:Europe/Moscow}") String zoneId,
                   @Value("${app.predictions.weeks-ahead:1}") int weeksAhead) {
        this.zone = ZoneId.of(zoneId);
        this.weeksAhead = weeksAhead;
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate weekOf(Instant instant) {
        return RoundWeeks.mondayOf(instant, zone);
    }

    public LocalDate currentWeek() {
        return weekOf(Instant.now());
    }

    /** Последняя неделя, на матчи которой уже можно делать прогнозы. */
    public LocalDate lastPredictableWeek() {
        return currentWeek().plusWeeks(weeksAhead);
    }
}
