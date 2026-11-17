package com.example.predictor.round;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Этап турнира показывается по-русски, а коды источника не показываются вовсе. */
class StageLabelsTest {

    @Test
    void knownCodesBecomeRussian() {
        assertEquals("Общий этап", StageLabels.of("LEAGUE_STAGE"));
        assertEquals("1/8 финала", StageLabels.of("LAST_16"));
        assertEquals("Финал", StageLabels.of("FINAL"));
    }

    @Test
    void emptyAndNoisyStagesAreHidden() {
        assertNull(StageLabels.of(null));
        assertNull(StageLabels.of("  "));
        assertNull(StageLabels.of("REGULAR_SEASON"));
        // незнакомый код источника прятать безопаснее, чем показывать латиницей
        assertNull(StageLabels.of("SOME_NEW_PHASE"));
    }

    @Test
    void handwrittenStageStaysAsWritten() {
        assertEquals("1/4 финала", StageLabels.of("1/4 финала"));
        assertEquals("Ответный матч", StageLabels.of("Ответный матч"));
    }
}
