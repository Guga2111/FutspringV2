package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.notification.dto.NotificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds NotificationDTOs for the list (one recipient) and the push (several). The pelada, daily and actor must
 * already be loaded (fetch joins); the recipient's team on TEAMS_SORTED comes from one grouped query.
 */
@Component
@RequiredArgsConstructor
public class NotificationDTOAssembler {

    private final TeamRepository teamRepository;

    private record TeamKey(Long userId, Long dailyId) { }

    public List<NotificationDTO> toDTOs(List<Notification> notifications) {
        List<Notification> sorted = notifications.stream()
                .filter(n -> n.getType() == NotificationType.TEAMS_SORTED && n.getDaily() != null)
                .toList();
        Map<TeamKey, NotificationDTO.TeamRef> teams = sorted.isEmpty() ? Map.of() : findTeams(sorted);

        return notifications.stream()
                .map(n -> NotificationDTO.from(n, n.getType() == NotificationType.TEAMS_SORTED && n.getDaily() != null
                        ? teams.get(new TeamKey(n.getRecipient().getId(), n.getDaily().getId())) : null))
                .toList();
    }

    private Map<TeamKey, NotificationDTO.TeamRef> findTeams(List<Notification> sorted) {
        Set<Long> userIds = sorted.stream().map(n -> n.getRecipient().getId()).collect(Collectors.toSet());
        Set<Long> dailyIds = sorted.stream().map(n -> n.getDaily().getId()).collect(Collectors.toSet());
        return teamRepository.findTeamsOfUsersInDailies(userIds, dailyIds).stream()
                .collect(Collectors.toMap(row -> new TeamKey((Long) row[0], (Long) row[1]),
                        row -> new NotificationDTO.TeamRef((String) row[2], (String) row[3]),
                        (a, b) -> a));
    }
}
