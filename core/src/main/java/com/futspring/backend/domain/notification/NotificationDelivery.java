package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.notification.email.NotificationEmailDispatcher;
import com.futspring.backend.domain.notification.inbox.NotificationPushService;
import com.futspring.backend.domain.notification.webpush.WebPushDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * What happens to new notifications once their transaction commits: the realtime push (default async pool), the
 * e-mail (its own paced single thread) and the Web Push to the recipients' devices (default async pool). None makes
 * the request that caused them wait, and each one's failure is only logged, so the others still go.
 */
@Component
@RequiredArgsConstructor
public class NotificationDelivery {

    private final NotificationPushService pushService;
    private final NotificationEmailDispatcher emailDispatcher;
    private final WebPushDispatcher webPushDispatcher;

    public void deliver(List<Long> notificationIds) {
        pushService.push(notificationIds);
        emailDispatcher.dispatch(notificationIds);
        webPushDispatcher.dispatch(notificationIds);
    }
}
