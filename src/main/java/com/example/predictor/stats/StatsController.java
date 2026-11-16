package com.example.predictor.stats;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.stats.StatsService.StatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Личная статистика участника в группе. */
@RestController
@RequestMapping("/api/groups/{groupId}/me/stats")
public class StatsController {

    private final AuthService auth;
    private final StatsService stats;

    public StatsController(AuthService auth, StatsService stats) {
        this.auth = auth;
        this.stats = stats;
    }

    @GetMapping
    public StatsView mine(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return stats.forUser(user, groupId);
    }
}
