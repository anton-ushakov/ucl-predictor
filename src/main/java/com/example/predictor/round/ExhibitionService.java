package com.example.predictor.round;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.RoundExhibition;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.repository.RoundExhibitionRepository;
import com.example.predictor.repository.RoundResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Участие вне конкурса: баллы идут в зачёт сезона, шаурма — мимо.
 *
 * Решение участник принимает сам и только до подведения итогов: после них долги уже записаны,
 * и выйти из конкурса задним числом — то же, что отказаться платить, проиграв.
 */
@Service
public class ExhibitionService {

    public record Answer(boolean exhibition, String message) {
    }

    private final GroupAccess access;
    private final RoundExhibitionRepository exhibition;
    private final RoundResultRepository results;

    public ExhibitionService(GroupAccess access, RoundExhibitionRepository exhibition,
                             RoundResultRepository results) {
        this.access = access;
        this.exhibition = exhibition;
        this.results = results;
    }

    /** Кто в этом туре играет вне конкурса. */
    @Transactional(readOnly = true)
    public Set<Long> of(Long groupId, LocalDate roundKey) {
        Set<Long> ids = new HashSet<>();
        for (RoundExhibition row : exhibition.findByGroupIdAndRoundKey(groupId, roundKey)) {
            ids.add(row.getUserId());
        }
        return ids;
    }

    @Transactional
    public Answer set(AppUser user, Long groupId, LocalDate roundKey, boolean out) {
        access.require(user, groupId);
        if (results.existsByGroupIdAndRoundWeek(groupId, roundKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Итоги тура уже подведены: решать про шаурму задним числом поздно");
        }
        RoundExhibition row = exhibition
                .findByGroupIdAndRoundKeyAndUserId(groupId, roundKey, user.getId()).orElse(null);
        if (out && row == null) {
            exhibition.save(new RoundExhibition(groupId, roundKey, user.getId(), Instant.now()));
        } else if (!out && row != null) {
            exhibition.delete(row);
        }
        return new Answer(out, out
                ? "Играете вне конкурса: баллы пойдут в зачёт сезона, шаурма вас не коснётся"
                : "Играете на шаурму вместе со всеми");
    }
}
