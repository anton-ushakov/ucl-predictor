package com.example.predictor.admin;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** Участники приложения: только для владельца сервера. */
@RestController
@RequestMapping("/api/admin/users")
public class AppUsersController {

    public record BlockRequest(@Size(max = 200) String reason) {
    }

    private final AuthService auth;
    private final AppUsersService users;

    public AppUsersController(AuthService auth, AppUsersService users) {
        this.auth = auth;
        this.users = users;
    }

    @GetMapping
    public AppUsersService.UsersView list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "query", required = false) String query) {
        AppUser me = auth.authenticate(authorization);
        return users.list(me, query);
    }

    @PostMapping("/{userId}/block")
    public AppUsersService.UserDto block(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) BlockRequest request) {
        AppUser me = auth.authenticate(authorization);
        return users.block(me, userId, request == null ? null : request.reason());
    }

    @PostMapping("/{userId}/unblock")
    public AppUsersService.UserDto unblock(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId) {
        AppUser me = auth.authenticate(authorization);
        return users.unblock(me, userId);
    }

    @PostMapping("/{userId}/approve")
    public AppUsersService.UserDto approve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId) {
        AppUser me = auth.authenticate(authorization);
        return users.approve(me, userId);
    }

    @DeleteMapping("/{userId}")
    public void reject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Long userId) {
        AppUser me = auth.authenticate(authorization);
        users.reject(me, userId);
    }
}
