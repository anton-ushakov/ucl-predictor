package com.example.predictor.todo;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.todo.TodoService.TodoView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Что сделать сейчас: одним списком по всем группам участника. */
@RestController
@RequestMapping("/api/todo")
public class TodoController {

    private final AuthService auth;
    private final TodoService todo;

    public TodoController(AuthService auth, TodoService todo) {
        this.auth = auth;
        this.todo = todo;
    }

    @GetMapping
    public TodoView list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.authenticate(authorization);
        return todo.forUser(user);
    }
}
