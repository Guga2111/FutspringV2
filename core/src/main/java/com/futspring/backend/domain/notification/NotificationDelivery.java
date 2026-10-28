package com.futspring.backend.domain.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * What happens to new notifications once their transaction commits: the realtime push (async, so the request
 * that caused them never waits). The e-mail dispatch plugs in here too.
 */
@Component
@RequiredArgsConstructor
public class NotificationDelivery {

    private final NotificationPushService pushService;

    public void deliver(List<Long> notificationIds) {
        pushService.push(notificationIds);
    }
}
