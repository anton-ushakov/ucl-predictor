package com.example.predictor.names;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.names.TeamCatalogService.CatalogView;
import com.example.predictor.names.TeamCatalogService.TeamDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** Справочник команд: клубы и сборные с эмблемами и флагами. */
@RestController
@RequestMapping("/api/teams")
public class TeamCatalogController {

    public record TeamRequest(@NotBlank @Size(max = 100) String name,
                              @Size(max = 300) String crest,
                              @Size(max = 8) String countryCode) {
    }

    private final AuthService auth;
    private final TeamCatalogService catalog;

    public TeamCatalogController(AuthService auth, TeamCatalogService catalog) {
        this.auth = auth;
        this.catalog = catalog;
    }

    /** kind: CLUB или NATIONAL. query — поиск по названию. */
    @GetMapping
    public CatalogView list(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @RequestParam(value = "kind", defaultValue = "CLUB") String kind,
                            @RequestParam(value = "query", required = false) String query) {
        AppUser me = auth.authenticate(authorization);
        return catalog.list(me, kind, query);
    }

    @PostMapping
    public TeamDto add(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestParam(value = "kind", defaultValue = "CLUB") String kind,
                       @Valid @RequestBody TeamRequest request) {
        AppUser user = auth.authenticate(authorization);
        return catalog.add(user, kind, request.name(), request.crest(), request.countryCode());
    }

    @PutMapping("/{teamId}")
    public TeamDto edit(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable("teamId") Long teamId,
                        @Valid @RequestBody TeamRequest request) {
        AppUser user = auth.authenticate(authorization);
        return catalog.edit(user, teamId, request.name(), request.crest(), request.countryCode());
    }

    @DeleteMapping("/{teamId}")
    public void remove(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable("teamId") Long teamId) {
        AppUser user = auth.authenticate(authorization);
        catalog.remove(user, teamId);
    }

    /** Заявки на справочник: видит и разбирает только владелец приложения. */
    @GetMapping("/api/teams/requests")
    public java.util.List<TeamCatalogService.RequestDto> requests(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.authenticate(authorization);
        return catalog.pending(user);
    }

    @PostMapping("/api/teams/requests/{id}/approve")
    public MessageDto approve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        AppUser user = auth.authenticate(authorization);
        return new MessageDto(catalog.approve(user, id));
    }

    @PostMapping("/api/teams/requests/{id}/reject")
    public MessageDto reject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        AppUser user = auth.authenticate(authorization);
        return new MessageDto(catalog.reject(user, id));
    }

    public record MessageDto(String message) {
    }
}
