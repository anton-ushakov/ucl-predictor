package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupSeason;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.repository.GroupSeasonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сезоны группы. Общий зачёт за всё время рано или поздно становится вечным списком, в котором
 * новичку уже не догнать старожилов. Сезон начинает счёт заново с текущей недели, а история
 * туров остаётся нетронутой: меняется только то, с какой недели складывать баллы.
 */
@Service
public class SeasonService {

    /** current=true: сезон, который идёт сейчас. */
    public record SeasonDto(Long id, String name, LocalDate startWeek, boolean current) {
    }

    public record SeasonsView(List<SeasonDto> seasons, Long currentId, boolean canManage,
                              String note) {
    }

    /** Границы сезона: от from включительно до to включительно (null — без края). */
    public record Range(LocalDate from, LocalDate to, String name) {
    }

    private final GroupAccess access;
    private final GroupSeasonRepository seasons;
    private final AppTime time;

    public SeasonService(GroupAccess access, GroupSeasonRepository seasons, AppTime time) {
        this.access = access;
        this.seasons = seasons;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public SeasonsView view(AppUser user, Long groupId) {
        Membership ms = access.require(user, groupId);
        List<GroupSeason> list = seasons.findByGroupIdOrderByStartWeekAsc(groupId);
        Long currentId = list.isEmpty() ? null : list.get(list.size() - 1).getId();
        List<SeasonDto> dtos = new ArrayList<>();
        for (GroupSeason s : list) {
            dtos.add(new SeasonDto(s.getId(), s.getName(), s.getStartWeek(),
                    s.getId().equals(currentId)));
        }
        String note = list.isEmpty()
                ? "Сезонов пока нет: зачёт считается за всё время."
                : "Зачёт считается с начала выбранного сезона. История туров остаётся полной.";
        return new SeasonsView(dtos, currentId, ms.isOwner(), note);
    }

    /**
     * Границы сезона для зачёта. seasonId = null — текущий сезон (или всё время, если сезонов нет),
     * seasonId = 0 — намеренно за всё время.
     */
    @Transactional(readOnly = true)
    public Range rangeOf(Long groupId, Long seasonId) {
        if (seasonId != null && seasonId == 0L) {
            return new Range(null, null, "За всё время");
        }
        List<GroupSeason> list = seasons.findByGroupIdOrderByStartWeekAsc(groupId);
        if (list.isEmpty()) {
            return new Range(null, null, "За всё время");
        }
        int index = list.size() - 1;
        if (seasonId != null) {
            index = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(seasonId)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сезон не найден");
            }
        }
        GroupSeason season = list.get(index);
        // сезон идёт до недели перед началом следующего
        LocalDate to = index + 1 < list.size()
                ? list.get(index + 1).getStartWeek().minusWeeks(1)
                : null;
        return new Range(season.getStartWeek(), to, season.getName());
    }

    /** Начать новый сезон с текущей недели. Только владелец. */
    @Transactional
    public SeasonsView start(AppUser user, Long groupId, String name) {
        access.requireOwner(user, groupId);
        String clean = name == null ? "" : name.trim();
        if (clean.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите название сезона");
        }
        if (clean.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название слишком длинное");
        }
        LocalDate week = time.currentWeek();
        for (GroupSeason s : seasons.findByGroupIdOrderByStartWeekAsc(groupId)) {
            if (s.getStartWeek().equals(week)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Сезон с этой недели уже начат: " + s.getName());
            }
        }
        seasons.save(new GroupSeason(groupId, clean, week, Instant.now(), user.getId()));
        return view(user, groupId);
    }

    /** Убрать сезон: туры и итоги остаются, меняется только разбивка зачёта. */
    @Transactional
    public SeasonsView remove(AppUser user, Long groupId, Long seasonId) {
        access.requireOwner(user, groupId);
        GroupSeason season = seasons.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сезон не найден"));
        if (!season.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сезон не найден");
        }
        seasons.delete(season);
        return view(user, groupId);
    }
}
