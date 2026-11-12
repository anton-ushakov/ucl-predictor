package com.example.predictor.round;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.round.SelectionDtos.CompetitionDto;
import com.example.predictor.round.SelectionDtos.ManualMatchRequest;
import com.example.predictor.round.SelectionDtos.ManualMatchResult;
import com.example.predictor.round.SelectionDtos.ManualScoreRequest;
import com.example.predictor.round.SelectionDtos.SelectRequest;
import com.example.predictor.round.SelectionDtos.SelectionResult;
import com.example.predictor.round.SelectionDtos.SelectionView;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Турниры, отбор матчей в тур и ручные матчи. Менять состав тура может только владелец группы. */
@RestController
@RequestMapping("/api/groups/{groupId}")
public class SelectionController {

    private final AuthService auth;
    private final MatchSelectionService service;

    public SelectionController(AuthService auth, MatchSelectionService service) {
        this.auth = auth;
        this.service = service;
    }

    /** Турниры, доступные для отбора матчей. */
    @GetMapping("/competitions")
    public List<CompetitionDto> competitions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return service.competitions(user, groupId);
    }

    /** Матчи турнира за неделю тура: что уже в туре, что можно добавить. */
    @GetMapping("/rounds/{week}/selection")
    public SelectionView selection(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(value = "competitionId", required = false) Long competitionId) {
        AppUser user = auth.authenticate(authorization);
        return service.selectable(user, groupId, week, competitionId);
    }

    /** Добавить отмеченные матчи в тур. Неделя тура берётся у самих матчей. */
    @PostMapping("/round-matches")
    @ResponseStatus(HttpStatus.CREATED)
    public SelectionResult add(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @PathVariable("groupId") Long groupId,
                               @Valid @RequestBody SelectRequest request) {
        AppUser user = auth.authenticate(authorization);
        return service.add(user, groupId, request.matchIds());
    }

    /** Убрать матч из тура (пока на него нет прогнозов). */
    @DeleteMapping("/round-matches/{matchId}")
    public SelectionResult remove(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable("groupId") Long groupId,
                                  @PathVariable("matchId") Long matchId) {
        AppUser user = auth.authenticate(authorization);
        return service.remove(user, groupId, matchId);
    }

    /** Добавить матч вручную: для турниров, которых нет в бесплатном тарифе источника. */
    @PostMapping("/manual-matches")
    @ResponseStatus(HttpStatus.CREATED)
    public ManualMatchResult createManual(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody ManualMatchRequest request) {
        AppUser user = auth.authenticate(authorization);
        return service.createManual(user, groupId, request);
    }

    /** Поправить ручной матч, пока на него нет прогнозов. */
    @PutMapping("/manual-matches/{matchId}")
    public ManualMatchResult editManual(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("matchId") Long matchId,
            @Valid @RequestBody ManualMatchRequest request) {
        AppUser user = auth.authenticate(authorization);
        return service.editManual(user, groupId, matchId, request);
    }

    /** Ввести счёт ручного матча после его окончания. */
    @PostMapping("/manual-matches/{matchId}/score")
    public ManualMatchResult setScore(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("matchId") Long matchId,
            @Valid @RequestBody ManualScoreRequest request) {
        AppUser user = auth.authenticate(authorization);
        return service.setScore(user, groupId, matchId, request);
    }

    /** Ручной матч не состоялся: не учитывать его в итогах тура. */
    @PostMapping("/manual-matches/{matchId}/cancel")
    public ManualMatchResult cancelManual(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("groupId") Long groupId,
            @PathVariable("matchId") Long matchId) {
        AppUser user = auth.authenticate(authorization);
        return service.cancelManual(user, groupId, matchId);
    }
}
