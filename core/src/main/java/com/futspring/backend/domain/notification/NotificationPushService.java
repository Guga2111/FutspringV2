package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.notification.dto.NotificationDTO;
import com.futspring.backend.domain.notification.dto.NotificationPushDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends committed notifications to their recipients over STOMP (/user/queue/notifications; the session principal
 * is the e-mail). Runs on the default async pool after the commit. A failure is only logged: the row is stored and
 * the client recounts on reconnect.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    static final String QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final NotificationDTOAssembler dtoAssembler;
    private final SimpMessagingTemplate messagingTemplate;

    // Three queries whatever the size: the rows with their references, the teams of TEAMS_SORTED, the counts
    @Async
    @Transactional(readOnly = true)
    public void push(List<Long> notificationIds) {
        try {
            List<Notification> notifications = notificationRepository.findAllByIdInWithRefs(notificationIds);
            if (notifications.isEmpty()) {
                return;
            }
            List<Long> recipientIds = notifications.stream().map(n -> n.getRecipient().getId()).distinct().toList();
            Map<Long, Long> unread = notificationRepository.countUnreadByRecipients(recipientIds).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
            List<NotificationDTO> dtos = dtoAssembler.toDTOs(notifications);
            for (int i = 0; i < notifications.size(); i++) {
                send(notifications.get(i), dtos.get(i), unread.getOrDefault(notifications.get(i).getRecipient().getId(), 0L));
            }
        } catch (RuntimeException e) {
            log.warn("Could not push notifications {}", notificationIds, e);
        }
    }

    // One recipient's failure doesn't stop the others
    private void send(Notification notification, NotificationDTO dto, long unreadCount) {
        try {
            messagingTemplate.convertAndSendToUser(notification.getRecipient().getEmail(), QUEUE,
                    new NotificationPushDTO(dto, unreadCount));
        } catch (RuntimeException e) {
            log.warn("Could not push notification {} to user {}", notification.getId(),
                    notification.getRecipient().getId(), e);
        }
    }
}
