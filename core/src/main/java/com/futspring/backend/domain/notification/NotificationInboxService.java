package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.notification.dto.NotificationDTO;
import com.futspring.backend.domain.notification.dto.UnreadCountDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The caller's notification center. Every method acts on the caller only (from the token). Reading a bump's
 * notification also dismisses the bump alert, and the other way round (DailyAttendanceService.markBumpsSeen).
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    static final int MAX_PAGE_SIZE = 50;

    private final UserAuthenticationHelper userAuthHelper;
    private final NotificationRepository notificationRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final TeamRepository teamRepository;
    private final Clock clock;

    // Newest first; a constant number of queries: the page (fetch joins) and the caller's teams of TEAMS_SORTED rows
    @Transactional(readOnly = true)
    public List<NotificationDTO> list(int page, int size, boolean unreadOnly, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        List<Notification> rows = notificationRepository.findPage(caller.getId(), unreadOnly, pageable);

        List<Long> sortedDailyIds = rows.stream()
                .filter(n -> n.getType() == NotificationType.TEAMS_SORTED && n.getDaily() != null)
                .map(n -> n.getDaily().getId())
                .distinct()
                .toList();
        Map<Long, NotificationDTO.TeamRef> teams = sortedDailyIds.isEmpty() ? Map.of()
                : teamRepository.findTeamsOfUserInDailies(caller.getId(), sortedDailyIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0],
                                row -> new NotificationDTO.TeamRef((String) row[1], (String) row[2]),
                                (a, b) -> a));

        return rows.stream()
                .map(n -> NotificationDTO.from(n, n.getType() == NotificationType.TEAMS_SORTED && n.getDaily() != null
                        ? teams.get(n.getDaily().getId()) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountDTO unreadCount(String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        return new UnreadCountDTO(notificationRepository.countByRecipientIdAndReadAtIsNull(caller.getId()));
    }

    // Idempotent; someone else's notification is answered like a missing one (404)
    @Transactional
    public void markRead(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Notification notification = notificationRepository.findByIdAndRecipientId(id, caller.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Notificação não encontrada"));
        if (notification.getReadAt() != null) {
            return;
        }
        Instant now = Instant.now(clock);
        notification.setReadAt(now);
        if (notification.getType() == NotificationType.DIARISTA_BUMPED && notification.getDaily() != null) {
            bumpRepository.markSeen(notification.getDaily(), caller, now);
        }
    }

    @Transactional
    public void markAllRead(String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Instant now = Instant.now(clock);
        List<Long> bumpedDailyIds = notificationRepository.findUnreadDailyIds(caller.getId(), NotificationType.DIARISTA_BUMPED);
        if (!bumpedDailyIds.isEmpty()) {
            bumpRepository.markSeenInDailies(caller, bumpedDailyIds, now);
        }
        notificationRepository.markAllRead(caller.getId(), now);
    }
}
