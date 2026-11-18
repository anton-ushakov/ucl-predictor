package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Формы данных для турниров, отбора матчей в тур и ручных матчей. */
public final class SelectionDtos {

    private SelectionDtos() {
    }

    /**
     * Турнир. synced=true: матчи приходят из football-data.org сами; synced=false: матчи
     * добавляет владелец группы вручную (так сделаны Лига Европы, чемпионат России и сборные —
     * их нет в бесплатном тарифе источника).
     */
    public record CompetitionDto(Long id, String code, String name, String shortName, boolean synced,
                                 boolean auto) {
    }

    /**
     * Матч в списке отбора. selected=true: матч уже в туре группы. hasPredictions=true: на матч
     * уже есть прогнозы, убрать его из тура нельзя.
     */
    public record SelectableMatch(Long matchId, String homeTeam, String awayTeam,
                                  String homeCrest, String awayCrest, Instant kickoffAt,
                                  Instant lockAt, String stage, Integer matchday, MatchStatus status,
                                  boolean manual, boolean selected, boolean locked,
                                  boolean hasPredictions, Integer homeScore, Integer awayScore,
                                  boolean inOtherRound) {
    }

    /**
     * Тур недели, в который можно положить матчи. Матч живёт ровно в одном туре, поэтому
     * сначала выбирают тур, а потом отмечают матчи.
     */
    public record RoundOption(LocalDate key, int no, String title, int matches,
                              Instant firstKickoff, Instant lastKickoff) {
    }

    /** Экран отбора матчей: турниры, матчи выбранного турнира и что с ними можно делать. */
    public record SelectionView(LocalDate week, Long competitionId, boolean canEdit,
                                boolean manualCompetition, boolean autoCompetition,
                                int selectedInRound,
                                List<CompetitionDto> competitions, List<SelectableMatch> matches,
                                String note,
                                LocalDate roundKey, int roundNo, String roundTitle,
                                List<RoundOption> rounds) {
    }

    /** Автосостав турнира: его матчи попадают в тур сами. */
    public record AutoRequest(boolean enabled) {
    }

    /** roundKey: в какой тур недели класть матчи. Не указан — первый тур недели матча. */
    public record SelectRequest(@NotEmpty(message = "Не выбрано ни одного матча") List<Long> matchIds,
                                LocalDate roundKey) {
    }

    /** Новый тур в неделе: название необязательное. */
    public record NewRoundRequest(@NotNull(message = "Укажите неделю") LocalDate week,
                                  @Size(max = 60) String title) {
    }

    public record RenameRoundRequest(@Size(max = 60) String title) {
    }

    /** Созданный или переименованный тур. */
    public record RoundInfo(LocalDate key, int no, String title, String message) {
    }

    /** Ручной матч: владелец вводит команды и время начала сам. */
    /**
     * Ручной матч. Команды либо выбраны из справочника (homeTeamId и awayTeamId), либо набраны
     * руками (homeTeam и awayTeam) — второе оставлено для старых версий приложения.
     */
    public record ManualMatchRequest(@NotNull(message = "Укажите турнир") Long competitionId,
                                     @Size(max = 100) String homeTeam,
                                     @Size(max = 100) String awayTeam,
                                     Long homeTeamId,
                                     Long awayTeamId,
                                     @NotNull(message = "Укажите время начала") Instant kickoffAt,
                                     @Size(max = 40) String stage) {
    }

    /**
     * Счёт ручного матча. Основное время обязательно; добавочное время и серия пенальти
     * заполняются только если они были (голы именно этого периода).
     */
    public record ManualScoreRequest(@NotNull(message = "Укажите счёт хозяев") @Min(0) @Max(99) Integer homeScore,
                                     @NotNull(message = "Укажите счёт гостей") @Min(0) @Max(99) Integer awayScore,
                                     @Min(0) @Max(99) Integer extraHome,
                                     @Min(0) @Max(99) Integer extraAway,
                                     @Min(0) @Max(99) Integer penHome,
                                     @Min(0) @Max(99) Integer penAway) {
    }

    public record SelectionResult(int selectedInRound, int changed, String message) {
    }

    public record ManualMatchResult(Long matchId, LocalDate week, String message) {
    }
}
