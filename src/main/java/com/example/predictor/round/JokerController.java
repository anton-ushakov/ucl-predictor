package com.example.predictor.round;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.round.JokerService.JokerResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Джокер тура: матч с удвоенными баллами. */
@RestController
@RequestMapping("/api/groups/{groupId}/rounds/{week}/joker")
public class JokerController {

    /** matchId=null: снять джокер. */
    public record JokerRequest(Long matchId) {
    }

    private final AuthService auth;
    private final JokerService jokers;

    public JokerController(AuthService auth, JokerService jokers) {
        this.auth = auth;
        this.jokers = jokers;
    }

    @PutMapping
    public JokerResult set(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @PathVariable("groupId") Long groupId,
                           @PathVariable("week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           @RequestBody JokerRequest request) {
        AppUser user = auth.authenticate(authorization);
        return jokers.set(user, groupId, week, request.matchId());
    }
}
