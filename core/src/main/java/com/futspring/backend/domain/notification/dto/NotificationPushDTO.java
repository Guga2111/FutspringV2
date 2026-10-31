package com.futspring.backend.domain.notification.dto;


// Frame sent to /user/queue/notifications: the new (or collapsed) notification and the recipient's unread count
// after it, so the client never has to guess whether a collapsed notification was already counted
public record NotificationPushDTO(
        NotificationDTO notification,
        long unreadCount) {
}
