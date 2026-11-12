package com.example.predictor.round;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.round.HistoryService.HistoryRound;
import com.example.predictor.round.HistoryService.LeaderRow;
import com.example.predictor.round.RoundDtos.ProposeRequest;
import com.example.predictor.round.RoundDtos.RoundSummary;
import com.example.predictor.round.RoundDtos.ScoreDecisionView;
import com.example.predictor.round.RoundDtos.VoteRequest;
import com.example.predictor.round.RoundDtos.RoundView;
import com.example.predictor.round.RoundDtos.SubmitRequest;
import com.example.predictor.round.RoundDtos.SubmitResult;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}")
public class RoundController {

    private final AuthService auth;
    private final RoundService rounds;
    private final HistoryService history;
    private final ScoreDecisionService decisions;

    public RoundController(AuthService auth, RoundService rounds, HistoryService history,
                           ScoreDecisionService decisions) {
        this.decisions = decisions;
        this.auth = auth;
        this.rounds = rounds;
        this.history = history;
    }

    /** Туры (недели) с матчами: текущая, следующая и недавние прошлые. */
    @GetMapping("/rounds")
    public List<RoundSummary> rounds(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return rounds.rounds(user, groupId);
    }

    /** Экран тура: матчи, мои прогнозы, чужие прогнозы (по правилам показа), таблица тура. */
    @GetMapping("/rounds/{week}")
    public RoundView round(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @PathVariable("groupId") Long groupId,
                           @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        AppUser user = auth.authenticate(authorization);
        return rounds.view(user, groupId, week);
    }

    /** Сохранить прогнозы на все открытые матчи тура. Изменить их после этого нельзя. */
    @PostMapping("/rounds/{week}/predictions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitResult submit(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @PathVariable("groupId") Long groupId,
                               @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                               @Valid @RequestBody SubmitRequest request) {
        AppUser user = auth.authenticate(authorization);
        return rounds.submit(user, groupId, week, request);
    }

    /**
     * Владелец решает, что делать с матчем, по которому нет данных о добавочном времени или пенальти:
     * предложить счёт по основному времени (true, нужно согласие остальных участников тура)
     * или не учитывать матч (false).
     */
    @PostMapping("/matches/{matchId}/score-decision")
    @ResponseStatus(HttpStatus.CREATED)
    public ScoreDecisionView propose(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable("groupId") Long groupId,
                                     @PathVariable("matchId") Long matchId,
                                     @Valid @RequestBody ProposeRequest request) {
        AppUser user = auth.authenticate(authorization);
        return decisions.propose(user, groupId, matchId, request.countRegularTimeOnly());
    }

    /** Голос участника тура по предложению владельца: согласен или нет. Голос окончательный. */
    @PostMapping("/matches/{matchId}/score-decision/vote")
    @ResponseStatus(HttpStatus.CREATED)
    public ScoreDecisionView vote(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable("groupId") Long groupId,
                                  @PathVariable("matchId") Long matchId,
                                  @Valid @RequestBody VoteRequest request) {
        AppUser user = auth.authenticate(authorization);
        return decisions.vote(user, groupId, matchId, request.agree());
    }

    /** История завершённых туров: места, баллы, победители и проигравшие. */
    @GetMapping("/history")
    public List<HistoryRound> history(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return history.history(user, groupId);
    }

    /** Общий зачёт группы по завершённым турам. */
    @GetMapping("/leaderboard")
    public List<LeaderRow> leaderboard(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return history.leaderboard(user, groupId);
    }
}
