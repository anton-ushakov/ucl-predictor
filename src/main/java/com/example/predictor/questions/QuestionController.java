package com.example.predictor.questions;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.questions.QuestionService.QuestionsView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Вопросы на турнир: длинные прогнозы вроде «кто выиграет Лигу чемпионов». */
@RestController
@RequestMapping("/api/groups/{groupId}/questions")
public class QuestionController {

    /** closesAt — время в формате ISO, например 2026-12-01T20:00:00Z. */
    public record CreateRequest(@NotBlank @Size(max = 200) String title,
                                @Size(max = 300) String hint,
                                @NotNull Integer points,
                                @NotNull Instant closesAt) {
    }

    public record AnswerRequest(@NotBlank @Size(max = 200) String answer) {
    }

    public record SettleRequest(@Size(max = 200) String correctAnswer, List<Long> correctUserIds) {
    }

    private final AuthService auth;
    private final QuestionService questions;

    public QuestionController(AuthService auth, QuestionService questions) {
        this.auth = auth;
        this.questions = questions;
    }

    @GetMapping
    public QuestionsView list(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @PathVariable("groupId") Long groupId) {
        AppUser user = auth.authenticate(authorization);
        return questions.view(user, groupId);
    }

    @PostMapping
    public QuestionsView create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable("groupId") Long groupId,
                                @Valid @RequestBody CreateRequest request) {
        AppUser user = auth.authenticate(authorization);
        return questions.create(user, groupId, request.title(), request.hint(), request.points(),
                request.closesAt());
    }

    @PutMapping("/{questionId}/answer")
    public QuestionsView answer(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable("groupId") Long groupId,
                                @PathVariable("questionId") Long questionId,
                                @Valid @RequestBody AnswerRequest request) {
        AppUser user = auth.authenticate(authorization);
        return questions.answer(user, groupId, questionId, request.answer());
    }

    /** Владелец отмечает правильный ответ и тех, кто угадал. */
    @PostMapping("/{questionId}/settle")
    public QuestionsView settle(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable("groupId") Long groupId,
                                @PathVariable("questionId") Long questionId,
                                @Valid @RequestBody SettleRequest request) {
        AppUser user = auth.authenticate(authorization);
        return questions.settle(user, groupId, questionId, request.correctAnswer(),
                request.correctUserIds());
    }

    @DeleteMapping("/{questionId}")
    public QuestionsView remove(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable("groupId") Long groupId,
                                @PathVariable("questionId") Long questionId) {
        AppUser user = auth.authenticate(authorization);
        return questions.remove(user, groupId, questionId);
    }
}
