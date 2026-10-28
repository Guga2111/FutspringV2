package com.futspring.backend.domain.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * What happens to new notifications once their transaction commits: the realtime push (default async pool) and
 * the e-mail (its own paced single thread). Neither makes the request that caused them wait.
 */
@Component
@RequiredArgsConstructor
public class NotificationDelivery {

    private final NotificationPushService pushService;
    private final NotificationEmailDispatcher emailDispatcher;

    public void deliver(List<Long> notificationIds) {
        pushService.push(notificationIds);
        emailDispatcher.dispatch(notificationIds);
    }
}
