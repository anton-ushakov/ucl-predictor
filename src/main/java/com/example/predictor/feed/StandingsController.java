package com.example.predictor.feed;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import org.springframework.web.bind.annotation.*;

/** Турнирные таблицы самих турниров. */
@RestController
public class StandingsController {

    private final AuthService auth;
    private final StandingsService standings;

    public StandingsController(AuthService auth, StandingsService standings) {
        this.auth = auth;
        this.standings = standings;
    }

    @GetMapping("/api/tables")
    public StandingsService.TablesView tables(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "competitionId", required = false) Long competitionId) {
        AppUser user = auth.authenticate(authorization);
        return standings.view(user, competitionId);
    }
}
