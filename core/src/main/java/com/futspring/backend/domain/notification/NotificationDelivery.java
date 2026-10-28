package com.futspring.backend.domain.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * What happens to new notifications once their transaction commits. Nothing yet: the realtime push and the
 * e-mail dispatch plug in here.
 */
@Slf4j
@Component
public class NotificationDelivery {

    public void deliver(List<Long> notificationIds) {
        log.debug("Notifications stored: {}", notificationIds);
    }
}
