package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.MatchRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds {@link DailyListItemDTO}s for the caller: counts and attendance with a constant number of queries. */
@Component
@RequiredArgsConstructor
public class DailyListItemAssembler {

    private final DailyRepository dailyRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    /** One daily, after a mutation (the confirmed players are already loaded by the caller). */
    public DailyListItemDTO toListItem(Daily daily, User caller) {
        return build(daily,
                daily.getConfirmedPlayers().size(),
                (int) teamRepository.countByDaily(daily),
                (int) matchRepository.countByDaily(daily),
                daily.getConfirmedPlayers().contains(caller));
    }

    /** A freshly created daily: nothing hangs from it yet. */
    public DailyListItemDTO toNewListItem(Daily daily) {
        return build(daily, 0, 0, 0, false);
    }

    /** A list of dailies: four grouped queries, whatever the list size. */
    public List<DailyListItemDTO> toListItems(List<Daily> dailies, User caller) {
        if (dailies.isEmpty()) {
            return List.of();
        }
        List<Long> ids = dailies.stream().map(Daily::getId).toList();
        Map<Long, Integer> confirmed = toCountMap(dailyRepository.countConfirmedByIds(ids));
        Map<Long, Integer> teams = toCountMap(teamRepository.countByDailyIds(ids));
        Map<Long, Integer> matches = toCountMap(matchRepository.countByDailyIds(ids));
        Set<Long> confirmedByCaller = new HashSet<>(dailyRepository.findConfirmedDailyIds(ids, caller.getId()));

        return dailies.stream()
                .map(d -> build(d,
                        confirmed.getOrDefault(d.getId(), 0),
                        teams.getOrDefault(d.getId(), 0),
                        matches.getOrDefault(d.getId(), 0),
                        confirmedByCaller.contains(d.getId())))
                .toList();
    }

    private static DailyListItemDTO build(Daily daily, int confirmedCount, int teamCount, int matchCount, boolean isConfirmed) {
        return DailyListItemDTO.builder()
                .id(daily.getId())
                .dailyDate(daily.getDailyDate())
                .dailyTime(daily.getDailyTime())
                .status(daily.getStatus())
                .confirmedPlayerCount(confirmedCount)
                .isFinished(daily.isFinished())
                .teamCount(teamCount)
                .matchCount(matchCount)
                .championImage(daily.getChampionImage())
                .isConfirmed(isConfirmed)
                .build();
    }

    private static Map<Long, Integer> toCountMap(Collection<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
    }
}
