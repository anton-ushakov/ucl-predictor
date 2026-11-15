package com.example.predictor.round;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.round.DebtService.DebtDto;
import com.example.predictor.round.DebtService.DebtsView;
import org.springframework.web.bind.annotation.*;

/** Долговая книга шаурмы. */
@RestController
@RequestMapping("/api/groups/{groupId}/debts")
public class DebtController {

    private final AuthService auth;
    private final DebtService debts;

    public DebtController(AuthService auth, DebtService debts) {
        this.auth = auth;
        this.debts = debts;
    }

    @GetMapping
    public DebtsView list(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return debts.view(user, groupId);
    }

    /** Шаурма отдана. Ставит тот, кому должны, или владелец группы. */
    @PostMapping("/{debtId}/settle")
    public DebtDto settle(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @PathVariable("groupId") Long groupId,
                          @PathVariable("debtId") Long debtId) {
        AppUser user = auth.authenticate(authorization);
        return debts.settle(user, groupId, debtId, true);
    }

    /** Отметку поставили по ошибке. */
    @PostMapping("/{debtId}/reopen")
    public DebtDto reopen(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @PathVariable("groupId") Long groupId,
                          @PathVariable("debtId") Long debtId) {
        AppUser user = auth.authenticate(authorization);
        return debts.settle(user, groupId, debtId, false);
    }
}
