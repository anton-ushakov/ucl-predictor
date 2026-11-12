package com.example.predictor.repository;

import java.time.LocalDate;

/** Пара "группа и тур", в которой есть хотя бы один прогноз. */
public record GroupRound(Long groupId, LocalDate roundWeek) {
}
