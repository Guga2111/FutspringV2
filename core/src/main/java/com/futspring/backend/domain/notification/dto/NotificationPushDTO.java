package com.futspring.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Frame sent to /user/queue/notifications: the new (or collapsed) notification and the recipient's unread count
// after it, so the client never has to guess whether a collapsed notification was already counted
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushDTO {

    private NotificationDTO notification;
    private long unreadCount;
}
