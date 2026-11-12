package com.example.predictor.names;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.names.TeamNamesService.SaveResult;
import com.example.predictor.names.TeamNamesService.TeamNamesView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** Словарь русских названий команд. Пополняет его владелец группы, видят все. */
@RestController
@RequestMapping("/api/groups/{groupId}/team-names")
public class TeamNamesController {

    /** original — название из списка команд, russian — что вписал владелец. */
    public record SaveRequest(@NotBlank(message = "Укажите, какое название переводим")
                              @Size(max = 100) String original,
                              @NotBlank(message = "Укажите русское название")
                              @Size(max = 100) String russian) {
    }

    private final AuthService auth;
    private final TeamNamesService service;

    public TeamNamesController(AuthService auth, TeamNamesService service) {
        this.auth = auth;
        this.service = service;
    }

    /** Команды из матчей: у кого есть русское название, а кого надо перевести. */
    @GetMapping
    public TeamNamesView list(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return service.view(user, groupId);
    }

    @PutMapping
    public SaveResult save(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @PathVariable("groupId") Long groupId,
                           @Valid @RequestBody SaveRequest request) {
        AppUser user = auth.authenticate(authorization);
        return service.save(user, groupId, request.original(), request.russian());
    }

    @DeleteMapping
    public SaveResult remove(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @PathVariable("groupId") Long groupId,
                             @RequestParam("original") String original) {
        AppUser user = auth.authenticate(authorization);
        return service.remove(user, groupId, original);
    }
}
